// Package runtime embeds the C runtime that ships with the compiler.
package runtime

import _ "embed"

// Header is tyrt.h.
//
//go:embed src/tyrt.h
var Header string

// Core is tyrt.c.
//
//go:embed src/tyrt.c
var Core string

// Extra is tyrt2.c.
//
//go:embed src/tyrt2.c
var Extra string
