// Package lib holds the Teyru standard library. It is written in Teyru, in the
// .teyru files next to this file, and compiled together with every user
// program: there is no bytecode, no separate runtime library, and no
// precompiled form. Editing one of these files changes the language.
package lib

import (
	"embed"
	"io/fs"
	"sort"
)

//go:embed *.teyru
var sources embed.FS

// File is one standard library source file.
type File struct {
	Name   string
	Source string
}

// Files lists the standard library in compilation order. The numeric prefix of
// each file name fixes that order, because a class may refer to one declared
// later in another file only after every header has been read.
func Files() []File {
	entries, err := fs.ReadDir(sources, ".")
	if err != nil {
		panic("lib: cannot read the embedded standard library: " + err.Error())
	}
	names := make([]string, 0, len(entries))
	for _, e := range entries {
		names = append(names, e.Name())
	}
	sort.Strings(names)
	out := make([]File, 0, len(names))
	for _, n := range names {
		data, err := sources.ReadFile(n)
		if err != nil {
			panic("lib: cannot read " + n + ": " + err.Error())
		}
		out = append(out, File{Name: n, Source: string(data)})
	}
	return out
}
