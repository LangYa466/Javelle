/* The C side of examples/native/main.teyru.
   Every symbol below is the one `teyru build --native-header` printed. */
#include "tyrt.h"

int32_t tyn_Native_add_I_I(int32_t a, int32_t b) { return a + b; }

void *tyn_Native_greet_String(void *who) {
  tystr *prefix = ty_str_new("hello, ", 7);
  return ty_str_concat(prefix, (tystr *)who);
}

int32_t tyn_Native_scale_I(void *self, int32_t v) {
  /* a Teyru object is a C struct: the receiver's fields are plain members */
  struct { tyobj obj; int32_t f_factor; } *me = self;
  return v * me->f_factor;
}
