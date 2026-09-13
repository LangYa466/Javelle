package lexer

import (
	"testing"

	"github.com/LangYa466/Teyru/internal/source"
)

func lex(src string) ([]Token, string) {
	d := &source.Diagnostics{}
	toks := Lex(source.NewFile("t.teyru", src), d)
	return toks, d.String()
}

func kinds(toks []Token) []Kind {
	out := make([]Kind, 0, len(toks))
	for _, t := range toks {
		out = append(out, t.Kind)
	}
	return out
}

func TestBasicTokens(t *testing.T) {
	toks, errs := lex("class A {}\n")
	if errs != "" {
		t.Fatalf("unexpected diagnostics: %s", errs)
	}
	want := []string{"class", "A", "{", "}", ""}
	if len(toks) != len(want) {
		t.Fatalf("got %d tokens, want %d", len(toks), len(want))
	}
	for i, w := range want {
		if toks[i].Text != w {
			t.Errorf("token %d = %q, want %q", i, toks[i].Text, w)
		}
	}
}

func TestNewlineFlag(t *testing.T) {
	toks, _ := lex("a\nb c\n")
	if !toks[1].NL {
		t.Error("token after a newline must carry the NL flag")
	}
	if toks[2].NL {
		t.Error("token on the same line must not carry the NL flag")
	}
}

func TestSemicolonIsRejected(t *testing.T) {
	_, errs := lex("int x = 1;")
	if errs == "" {
		t.Fatal("a semicolon must be rejected")
	}
	if !contains(errs, "TY-SYN-0001") {
		t.Errorf("wrong diagnostic: %s", errs)
	}
}

func TestStringEscapes(t *testing.T) {
	toks, errs := lex(`"a\tb\nA\101"`)
	if errs != "" {
		t.Fatalf("unexpected diagnostics: %s", errs)
	}
	got := toks[0].Text
	want := "a\tb\nAA"
	if got != want {
		t.Errorf("got %q, want %q", got, want)
	}
}

func TestTextBlock(t *testing.T) {
	toks, errs := lex("\"\"\"\n  hello\n  world\n  \"\"\"\n")
	if errs != "" {
		t.Fatalf("unexpected diagnostics: %s", errs)
	}
	if toks[0].Kind != StringLit {
		t.Fatalf("expected a string literal, got %v", kinds(toks))
	}
	// JLS 3.10.6: the content ends just before the closing delimiter, so the
	// line terminator after the last line of text is part of it.
	if toks[0].Text != "hello\nworld\n" {
		t.Errorf("text block = %q", toks[0].Text)
	}
}

func TestTextBlockClosingOnTextLine(t *testing.T) {
	toks, errs := lex("\"\"\"\n  hello \"\"\"\n")
	if errs != "" {
		t.Fatalf("unexpected diagnostics: %s", errs)
	}
	// ... and trailing white space is stripped from every line, so the
	// content starts at the first non-blank character.
	if toks[0].Text != "hello" {
		t.Errorf("text block = %q", toks[0].Text)
	}
}

func TestNumbers(t *testing.T) {
	toks, errs := lex("0 1_000 0x1F 0b1010 017 1.5 2e3 4L 5.0f 'x'")
	if errs != "" {
		t.Fatalf("unexpected diagnostics: %s", errs)
	}
	wantInts := []uint64{0, 1000, 31, 10, 15}
	for i, w := range wantInts {
		if toks[i].Int != w {
			t.Errorf("literal %d = %d, want %d", i, toks[i].Int, w)
		}
	}
	if toks[7].Kind != LongLit {
		t.Errorf("4L should be a long literal, got %v", toks[7].Kind)
	}
	if toks[9].Kind != CharLit || toks[9].Int != 'x' {
		t.Errorf("char literal wrong: %+v", toks[9])
	}
}

func TestUnterminatedString(t *testing.T) {
	_, errs := lex(`"abc`)
	if !contains(errs, "TY-SYN-0006") {
		t.Errorf("expected unterminated string diagnostic, got %s", errs)
	}
}

func contains(haystack, needle string) bool {
	return len(haystack) >= len(needle) && (haystack == needle || indexOf(haystack, needle) >= 0)
}

func indexOf(h, n string) int {
	for i := 0; i+len(n) <= len(h); i++ {
		if h[i:i+len(n)] == n {
			return i
		}
	}
	return -1
}
