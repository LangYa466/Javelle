// Package prelude embeds the Teyru standard library, which is written in
// Teyru itself and compiled together with every user program.
package prelude

// Source is the standard library shipped with the compiler.
const Source = `package teyru

class Object {
  public Object() {
  }
  public native String toString()
  public native int hashCode()
  public native boolean equals(Object o)
  public native Class getClass()
}

class Class {
  public native String getName()
  public String toString() {
    return getName()
  }
}

interface Cloneable {
}

interface Comparable<T> {
  int compareTo(T o)
}

interface AutoCloseable {
  void close()
}

interface Iterable<T> {
  Iterator<T> iterator()
}

interface Iterator<T> {
  boolean hasNext()
  T next()
}

class String extends Object {
  public native int length()
  public native boolean isEmpty()
  public native char charAt(int index)
  public native boolean equals(Object o)
  public native int hashCode()
  public native int indexOf(String s)
  public native String substring(int from)
  public native String substring(int from, int to)
  public native String toUpperCase()
  public native String toLowerCase()
  public native String trim()
  public native boolean contains(String s)
  public native boolean startsWith(String s)
  public native boolean endsWith(String s)
  public native String replace(char a, char b)
  public native int compareTo(String s)
  public native String concat(String s)
  public native String toString()
  public static native String valueOf(int v)
  public static native String valueOf(long v)
  public static native String valueOf(double v)
  public static native String valueOf(float v)
  public static native String valueOf(boolean v)
  public static native String valueOf(char v)
  public static native String valueOf(Object v)
}

class StringBuilder extends Object {
  public native StringBuilder append(String s)
  public native StringBuilder append(Object o)
  public native StringBuilder append(int v)
  public native StringBuilder append(long v)
  public native StringBuilder append(char c)
  public native StringBuilder append(double v)
  public native StringBuilder append(boolean v)
  public native String toString()
  public native int length()
}

class Math {
  public static final double PI = 3.141592653589793
  public static native int abs(int v)
  public static native long abs(long v)
  public static native double abs(double v)
  public static native int max(int a, int b)
  public static native int min(int a, int b)
  public static native long max(long a, long b)
  public static native long min(long a, long b)
  public static native double max(double a, double b)
  public static native double min(double a, double b)
  public static native double sqrt(double v)
  public static native double pow(double a, double b)
  public static native double floor(double v)
  public static native double ceil(double v)
  public static native long round(double v)
  public static native double random()
}

class PrintStream extends Object {
  public native void print(String s)
  public native void print(Object o)
  public native void print(int v)
  public native void print(long v)
  public native void print(double v)
  public native void print(boolean v)
  public native void print(char c)
  public native void println()
  public native void println(String s)
  public native void println(Object o)
  public native void println(int v)
  public native void println(long v)
  public native void println(double v)
  public native void println(boolean v)
  public native void println(char c)
}

class IO {
  public static native void print(String s)
  public static native void print(Object o)
  public static native void print(int v)
  public static native void print(long v)
  public static native void print(double v)
  public static native void print(boolean v)
  public static native void print(char c)
  public static native void println()
  public static native void println(String s)
  public static native void println(Object o)
  public static native void println(int v)
  public static native void println(long v)
  public static native void println(double v)
  public static native void println(boolean v)
  public static native void println(char c)
  public static native String readln()
}

class System {
  public static final PrintStream out = new PrintStream()
  public static final PrintStream err = new PrintStream()
  public static native long currentTimeMillis()
  public static native long nanoTime()
  public static native void exit(int status)
  public static native void arraycopy(Object src, int srcPos, Object dest, int destPos, int length)
}

class Integer extends Object implements Comparable<Integer> {
  public static native int parseInt(String s)
  public static native Integer valueOf(int v)
  public static native int compare(int a, int b)
  public static native int max(int a, int b)
  public static native int min(int a, int b)
  public native int intValue()
  public native int hashCode()
  public native boolean equals(Object o)
  public native int compareTo(Integer o)
  public native String toString()
  public static native String toString(int v)
}

class Long extends Object implements Comparable<Long> {
  public static native long parseLong(String s)
  public static native Long valueOf(long v)
  public static native int compare(long a, long b)
  public static native long max(long a, long b)
  public static native long min(long a, long b)
  public native long longValue()
  public native int intValue()
  public native int hashCode()
  public native boolean equals(Object o)
  public native int compareTo(Long o)
  public native String toString()
  public static native String toString(long v)
}

class Double extends Object implements Comparable<Double> {
  public static native double parseDouble(String s)
  public static native Double valueOf(double v)
  public static native int compare(double a, double b)
  public static native boolean isNaN(double v)
  public native double doubleValue()
  public native int hashCode()
  public native boolean equals(Object o)
  public native int compareTo(Double o)
  public native String toString()
  public static native String toString(double v)
}

class Float extends Object {
  public static native float parseFloat(String s)
  public static native Float valueOf(float v)
  public native float floatValue()
  public native String toString()
}

class Boolean extends Object {
  public static native boolean parseBoolean(String s)
  public static native Boolean valueOf(boolean v)
  public native boolean booleanValue()
  public native int hashCode()
  public native boolean equals(Object o)
  public native String toString()
}

class Character extends Object {
  public static native Character valueOf(char c)
  public static native boolean isDigit(char c)
  public static native boolean isLetter(char c)
  public static native boolean isWhitespace(char c)
  public native char charValue()
  public native String toString()
}

class Byte extends Object {
  public static native Byte valueOf(byte v)
  public native byte byteValue()
  public native int hashCode()
  public native String toString()
}

class Short extends Object {
  public static native Short valueOf(short v)
  public native short shortValue()
  public native int hashCode()
  public native String toString()
}

class Throwable extends Object {
  public String message
  public Throwable cause
  public Throwable() {
  }
  public Throwable(String message) {
    this.message = message
  }
  public String getMessage() {
    return message
  }
  public String toString() {
    if (message == null) {
      return "Throwable"
    }
    return message
  }
}

class Exception extends Throwable {
  public Exception() {
  }
  public Exception(String message) {
    super(message)
  }
}

class RuntimeException extends Exception {
  public RuntimeException() {
  }
  public RuntimeException(String message) {
    super(message)
  }
}

class NullPointerException extends RuntimeException {
  public NullPointerException() {
  }
  public NullPointerException(String message) {
    super(message)
  }
}

class ArithmeticException extends RuntimeException {
  public ArithmeticException() {
  }
  public ArithmeticException(String message) {
    super(message)
  }
}

class ArrayIndexOutOfBoundsException extends RuntimeException {
  public ArrayIndexOutOfBoundsException() {
  }
  public ArrayIndexOutOfBoundsException(String message) {
    super(message)
  }
}

class ClassCastException extends RuntimeException {
  public ClassCastException() {
  }
  public ClassCastException(String message) {
    super(message)
  }
}

class IllegalArgumentException extends RuntimeException {
  public IllegalArgumentException() {
  }
  public IllegalArgumentException(String message) {
    super(message)
  }
}

class IllegalStateException extends RuntimeException {
  public IllegalStateException() {
  }
  public IllegalStateException(String message) {
    super(message)
  }
}

class NoSuchElementException extends RuntimeException {
  public NoSuchElementException() {
  }
  public NoSuchElementException(String message) {
    super(message)
  }
}

class NegativeArraySizeException extends RuntimeException {
  public NegativeArraySizeException() {
  }
  public NegativeArraySizeException(String message) {
    super(message)
  }
}

class AssertionError extends RuntimeException {
  public AssertionError() {
  }
  public AssertionError(String message) {
    super(message)
  }
}

class UnsupportedOperationException extends RuntimeException {
  public UnsupportedOperationException() {
  }
  public UnsupportedOperationException(String message) {
    super(message)
  }
}

class Enum<E> extends Object implements Comparable<E> {
  public int ordinal
  public String name
  public native int ordinal()
  public native String name()
  public native String toString()
  public native int hashCode()
  public native boolean equals(Object o)
  public native int compareTo(E o)
}

class Record extends Object {
}
`
