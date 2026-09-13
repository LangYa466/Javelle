package main

import (
	"fmt"
	"os"
	"runtime"
	"time"

	"github.com/LangYa466/Teyru/internal/driver"
)

func main() {
	go func() {
		time.Sleep(5 * time.Second)
		buf := make([]byte, 1<<20)
		n := runtime.Stack(buf, true)
		os.Stderr.Write(buf[:n])
		os.Exit(3)
	}()
	_, err := driver.Compile([]string{"/tmp/hello.teyru"}, driver.Options{Out: "/tmp/hello", CFile: "/tmp/hello.c"})
	fmt.Println("err:", err)
}
