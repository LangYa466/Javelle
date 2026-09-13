#define _GNU_SOURCE 1
/* tyrt.c - Teyru native runtime. */
#include "tyrt.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <stdarg.h>
#include <pthread.h>

#if defined(__has_feature)
#if __has_feature(address_sanitizer)
#define TY_ASAN 1
void __asan_unpoison_memory_region(void *, size_t);
#endif
#endif

tycatch *ty_cur_catch = NULL;
tyclass *TY_STRING = NULL;
tyclass *TY_BOX[9] = {0};
tyclass *TY_OBJECT = NULL;

tyclass *TY_NPE, *TY_AIOOBE, *TY_ARITH, *TY_CCE, *TY_NEGARR, *TY_ASSERT;
tyclass *TY_ILLARG, *TY_ILLSTATE, *TY_NOSUCHELEM, *TY_UNSUP;

/* ------------------------------------------------------------------ GC */

#define TY_HDR 16
#define TY_CHUNK (1u << 18)
#define TY_ALIGN 16

typedef struct tychunk {
  struct tychunk *next;
  size_t used, cap;
  char *mem;
} tychunk;

static tychunk *chunks = NULL;
static void **roots_static = NULL; /* addresses of global slots */
static size_t nroots_static = 0, caproots_static = 0;
static char *stack_top = NULL;   /* highest address of the current thread stack */
static int64_t alloc_since_gc = 0;
static int64_t gc_threshold = 4 << 20;
static int64_t live_bytes = 0;
static int gc_disabled = 0;

void *ty_roots[TY_SHADOW_MAX];
int64_t ty_sp = 0;

#define TY_MARK_BIT 1u

static inline uint64_t *hdr_of(void *obj) { return (uint64_t *)((char *)obj - TY_HDR); }

void ty_gc_register_static(void *p) {
  if (nroots_static == caproots_static) {
    caproots_static = caproots_static ? caproots_static * 2 : 64;
    roots_static = (void **)realloc(roots_static, caproots_static * sizeof(void *));
  }
  roots_static[nroots_static++] = p;
}

static int in_heap(char *p) {
  for (tychunk *c = chunks; c; c = c->next) {
    if (p >= c->mem && p < c->mem + c->used) return 1;
  }
  return 0;
}

static int valid_obj(char *p) {
  if (((uintptr_t)p) & (TY_ALIGN - 1)) return 0;
  for (tychunk *c = chunks; c; c = c->next) {
    if (p >= c->mem && p + TY_HDR <= c->mem + c->used) {
      uint64_t sz = *(uint64_t *)(p - TY_HDR);
      if (sz >= TY_HDR && ((uintptr_t)(p - TY_HDR)) % TY_ALIGN == 0) return 1;
    }
  }
  return 0;
}

static void *mark_stack[1 << 20];
static size_t mark_sp = 0;

static void trace_object(void *obj);

static void mark_value(void *p) {
  if (!p) return;
  if (!valid_obj((char *)p)) return;
  uint64_t *h = hdr_of(p);
  if (h[1] & TY_MARK_BIT) return;
  h[1] |= TY_MARK_BIT;
  if (mark_sp < (1 << 20)) mark_stack[mark_sp++] = p;
}

static void trace_object(void *obj) {
  tyclass *c = ((tyobj *)obj)->cls;
  if (c && (c->flags & 2)) { /* array */
    tyarr *a = (tyarr *)obj;
    if (a->refs) {
      void **d = (void **)a->data;
      for (int64_t i = 0; i < a->len; i++) mark_value(d[i]);
    }
    return;
  }
  if (c && c->nref) {
    for (int32_t i = 0; i < c->nref; i++) {
      mark_value(*(void **)((char *)obj + c->refoffs[i]));
    }
  }
}

void ty_gc(void) {
  if (gc_disabled) return;
  mark_sp = 0;
  /* roots: shadow stack */
  for (int64_t i = 0; i < ty_sp; i++) mark_value(ty_roots[i]);
  /* roots: registered globals */
  for (size_t i = 0; i < nroots_static; i++) {
    if (roots_static[i]) mark_value(*(void **)roots_static[i]);
  }
  /* roots: native stack and registers (conservative) */
  jmp_buf regs;
  setjmp(regs);
  char *sp = (char *)&regs;
  char *hi = stack_top ? stack_top : sp + 0x10000;
  char *lo = sp;
#ifdef TY_ASAN
  if (hi > lo) __asan_unpoison_memory_region(lo, (size_t)(hi - lo));
#endif
  for (char *q = lo; q + sizeof(void *) <= hi; q += sizeof(void *)) {
    void *v = *(void **)q;
    mark_value(v);
  }
  /* trace */
  while (mark_sp) {
    void *o = mark_stack[--mark_sp];
    trace_object(o);
  }
  /* sweep: unmarked objects become free; the block goes on a free list. */
  live_bytes = 0;
  for (tychunk *c = chunks; c; c = c->next) {
    char *p = c->mem;
    while (p < c->mem + c->used) {
      uint64_t sz = *(uint64_t *)(p);
      uint64_t *h = (uint64_t *)(p + 8);
      if (!(*h & TY_MARK_BIT)) {
        ty_free_block(p + TY_HDR, sz);
      } else {
        *h &= ~(uint64_t)TY_MARK_BIT;
        live_bytes += (int64_t)sz;
      }
      p += sz;
    }
  }
  alloc_since_gc = 0;
  gc_threshold = live_bytes * 2;
  if (gc_threshold < (4 << 20)) gc_threshold = 4 << 20;
}

/* ------------------------------------------------------------------ allocation */

#define TY_NCLASS 64
static void *freelist[TY_NCLASS];
static void *bigfree = NULL;

static int size_class(size_t sz) {
  size_t i = (sz + TY_ALIGN - 1) / TY_ALIGN;
  return i < TY_NCLASS ? (int)i : -1;
}

void ty_free_block(void *payload, size_t total) {
  int k = size_class(total);
  void *p = (char *)payload - TY_HDR;
  if (k >= 0) {
    *(void **)((char *)p + 8) = freelist[k];
    freelist[k] = p;
  } else {
    *(void **)((char *)p + 8) = bigfree;
    bigfree = p;
  }
}

static void *alloc_slow(size_t total) {
  int k = size_class(total);
  if (k >= 0) {
    if (freelist[k]) {
      void *p = freelist[k];
      freelist[k] = *(void **)((char *)p + 8);
      return p;
    }
  } else {
    void **pp = &bigfree;
    while (*pp) {
      void *p = *pp;
      uint64_t sz = *(uint64_t *)p;
      if (sz >= total) {
        *pp = *(void **)((char *)p + 8);
        return p;
      }
      pp = (void **)((char *)p + 8);
    }
  }
  return NULL;
}

void *ty_alloc(size_t size) {
  size_t total = (size + TY_HDR + TY_ALIGN - 1) & ~(size_t)(TY_ALIGN - 1);
  if (alloc_since_gc > gc_threshold) ty_gc();
  void *p = alloc_slow(total);
  if (!p) {
    tychunk *c = chunks;
    if (!c || c->used + total > c->cap) {
      ty_gc();
      p = alloc_slow(total);
      if (p) goto done;
      size_t cap = total > TY_CHUNK ? ((total + TY_CHUNK - 1) & ~(size_t)(TY_CHUNK - 1)) : TY_CHUNK;
      c = (tychunk *)malloc(sizeof(tychunk));
      c->mem = (char *)malloc(cap);
      c->cap = cap;
      c->used = 0;
      c->next = chunks;
      chunks = c;
    }
    p = c->mem + c->used;
    c->used += total;
  }
done:;
  *(uint64_t *)p = (uint64_t)total;
  *(uint64_t *)((char *)p + 8) = 0;
  alloc_since_gc += (int64_t)total;
  void *obj = (char *)p + TY_HDR;
  memset(obj, 0, total - TY_HDR);
  return obj;
}

void ty_gc_init(void) {
  pthread_attr_t attr;
  if (pthread_getattr_np(pthread_self(), &attr) == 0) {
    void *base = NULL;
    size_t size = 0;
    if (pthread_attr_getstack(&attr, &base, &size) == 0) {
      stack_top = (char *)base + size;
    }
    pthread_attr_destroy(&attr);
  }
  for (int i = 0; i < TY_NCLASS; i++) freelist[i] = NULL;
}

void *ty_alloc_arr(int64_t len, size_t elemsize) {
  if (len < 0) ty_throw((tyobj *)ty_negarr());
  size_t bytes = (size_t)len * elemsize;
  if (len != 0 && bytes / (size_t)len != elemsize) ty_throw((tyobj *)ty_negarr());
  tyarr *a = (tyarr *)ty_alloc(sizeof(tyarr) + bytes);
  a->len = len;
  a->data = (char *)a + sizeof(tyarr);
  a->esize = (int32_t)elemsize;
  a->refs = 0;
  return a;
}

/* ------------------------------------------------------------------ exceptions */

void ty_uncaught(void *p) {
  tyobj *e = (tyobj*)p;
  tystr *s = ((tystr *(*)(void *))e->cls->vtable[0])(e);
  char *msg = s ? s->data : (char *)"?";
  fprintf(stderr, "Exception in thread \"main\" %s: %.*s\n", e->cls->name, (int)(s ? s->len : 1), msg);
  exit(1);
}

void ty_throw(void *e) {
  if (!ty_cur_catch) ty_uncaught(e);
  ty_cur_catch->ex = (tyobj*)e;
  longjmp(ty_cur_catch->buf, 1);
}

static tyobj *make_ex(tyclass *c, const char *msg) {
  tystr *m = ty_str_new(msg, (int64_t)strlen(msg));
  tyobj *o = (tyobj *)ty_alloc(sizeof(tyobj) + 2 * sizeof(void *));
  o->cls = c;
  ((void **)((char *)o + sizeof(tyobj)))[0] = m;
  ((void **)((char *)o + sizeof(tyobj)))[1] = NULL;
  return o;
}

void *ty_npe(void) {
  tystr *m = ty_str_intern("null");
  ty_throw(make_ex(TY_NPE, "null"));
  return m;
}
void *ty_aioobe(int64_t idx, int64_t len) {
  char buf[128];
  snprintf(buf, sizeof buf, "index %lld out of bounds for length %lld", (long long)idx, (long long)len);
  ty_throw(make_ex(TY_AIOOBE, buf));
  return NULL;
}
void *ty_arith(const char *msg) {
  ty_throw(make_ex(TY_ARITH, msg));
  return NULL;
}
void *ty_cce(tyclass *from, tyclass *to) {
  char buf[256];
  snprintf(buf, sizeof buf, "class %s cannot be cast to class %s", from ? from->name : "?", to ? to->name : "?");
  ty_throw(make_ex(TY_CCE, buf));
  return NULL;
}
void *ty_negarr(void) {
  ty_throw(make_ex(TY_NEGARR, "Negative array size"));
  return NULL;
}
void *ty_assertfail(const char *msg) {
  ty_throw(make_ex(TY_ASSERT, msg ? msg : "assertion failed"));
  return NULL;
}

int32_t ty_instanceof(void *p, tyclass *c) {
  tyobj *o = (tyobj *)p;
  if (!o) return 0;
  tyclass *k = o->cls;
  if (!k) return 0;
  if (k == c) return 1;
  if (c->flags & 1) {
    for (int32_t i = 0; i < k->niface; i++)
      if (k->ifaces[i] == c) return 1;
    return 0;
  }
  for (tyclass *s = k->super; s; s = s->super)
    if (s == c) return 1;
  return 0;
}

void *ty_checkcast(void *o, tyclass *c) {
  if (!o) return NULL;
  if (ty_instanceof(o, c)) return o;
  return ty_cce(((tyobj *)o)->cls, c);
}

void *ty_itab(void *p, int32_t sel) {
  tyobj *o = (tyobj *)p;
  if (!o) ty_npe();
  tyclass *c = o->cls;
  if (sel < c->isel && c->imap[sel].fn) return c->imap[sel].fn;
  /* search superclasses' maps (interfaces implemented by supers) */
  for (tyclass *k = c->super; k; k = k->super) {
    if (sel < k->isel && k->imap[sel].fn) return k->imap[sel].fn;
  }
  return NULL;
}

/* ------------------------------------------------------------------ strings */

tystr *ty_str_new(const char *data, int64_t len) {
  tystr *s = (tystr *)ty_alloc(sizeof(tystr) + (size_t)len + 1);
  s->obj.cls = TY_STRING;
  s->len = len;
  s->data = (char *)s + sizeof(tystr);
  if (data) memcpy(s->data, data, (size_t)len);
  s->data[len] = 0;
  return s;
}

tystr *ty_str_intern(const char *data) { return ty_str_new(data, (int64_t)strlen(data)); }

int64_t ty_str_len(tystr *s) { return s ? s->len : 0; }

tystr *ty_str_concat(tystr *a, tystr *b) {
  if (!a) a = ty_str_intern("null");
  if (!b) b = ty_str_intern("null");
  tystr *r = (tystr *)ty_alloc(sizeof(tystr) + (size_t)(a->len + b->len) + 1);
  r->obj.cls = TY_STRING;
  r->len = a->len + b->len;
  r->data = (char *)r + sizeof(tystr);
  memcpy(r->data, a->data, (size_t)a->len);
  memcpy(r->data + a->len, b->data, (size_t)b->len);
  r->data[r->len] = 0;
  return r;
}

int32_t ty_str_eq(tystr *a, tystr *b) {
  if (a == b) return 1;
  if (!a || !b) return 0;
  if (a->len != b->len) return 0;
  return memcmp(a->data, b->data, (size_t)a->len) == 0;
}

int32_t ty_str_cmp(tystr *a, tystr *b) {
  if (a == b) return 0;
  if (!a) return -1;
  if (!b) return 1;
  int64_t n = a->len < b->len ? a->len : b->len;
  int r = memcmp(a->data, b->data, (size_t)n);
  if (r) return r;
  return a->len < b->len ? -1 : (a->len > b->len ? 1 : 0);
}

int32_t ty_str_hash(tystr *s) {
  if (!s) return 0;
  int32_t h = 0;
  for (int64_t i = 0; i < s->len; i++) h = 31 * h + (unsigned char)s->data[i];
  return h;
}

int32_t ty_obj_hash(void *o) {
  if (!o) return 0;
  return ((int32_t (*)(void *))((tyobj *)o)->cls->vtable[1])(o);
}

int32_t ty_obj_eq(void *a, void *b) { return a == b; }

tystr *ty_str_of_long(int64_t v) {
  char buf[32];
  int n = snprintf(buf, sizeof buf, "%lld", (long long)v);
  return ty_str_new(buf, n);
}
tystr *ty_str_of_int(int64_t v) { return ty_str_of_long(v); }
tystr *ty_str_of_bool(int32_t v) { return ty_str_intern(v ? "true" : "false"); }
tystr *ty_str_of_char(uint16_t c) {
  char buf[4];
  int n = 0;
  if (c < 0x80) {
    buf[n++] = (char)c;
  } else if (c < 0x800) {
    buf[n++] = (char)(0xC0 | (c >> 6));
    buf[n++] = (char)(0x80 | (c & 0x3F));
  } else {
    buf[n++] = (char)(0xE0 | (c >> 12));
    buf[n++] = (char)(0x80 | ((c >> 6) & 0x3F));
    buf[n++] = (char)(0x80 | (c & 0x3F));
  }
  return ty_str_new(buf, n);
}
tystr *ty_str_of_double(double v) {
  char buf[64];
  if (v == (double)(int64_t)v && fabs(v) < 1e15) {
    int n = snprintf(buf, sizeof buf, "%.1f", v);
    return ty_str_new(buf, n);
  }
  int n = snprintf(buf, sizeof buf, "%.17g", v);
  return ty_str_new(buf, n);
}
tystr *ty_str_of_float(float v) {
  char buf[64];
  if (v == (float)(int64_t)v && fabsf(v) < 1e7f) {
    int n = snprintf(buf, sizeof buf, "%.1f", (double)v);
    return ty_str_new(buf, n);
  }
  int n = snprintf(buf, sizeof buf, "%.9g", (double)v);
  return ty_str_new(buf, n);
}

tystr *ty_object_tostring(void *o) {
  if (!o) return ty_str_intern("null");
  char buf[128];
  int n = snprintf(buf, sizeof buf, "%s@%llx", ((tyobj *)o)->cls->name, (unsigned long long)(uintptr_t)o);
  return ty_str_new(buf, n);
}

tystr *ty_str_of_obj(void *o) {
  if (!o) return ty_str_intern("null");
  return ((tystr *(*)(void *))((tyobj *)o)->cls->vtable[0])(o);
}

tystr *ty_str_upper(tystr *s) {
  if (!s) return NULL;
  tystr *r = ty_str_new(s->data, s->len);
  for (int64_t i = 0; i < r->len; i++)
    if (r->data[i] >= 'a' && r->data[i] <= 'z') r->data[i] -= 32;
  return r;
}
tystr *ty_str_lower(tystr *s) {
  if (!s) return NULL;
  tystr *r = ty_str_new(s->data, s->len);
  for (int64_t i = 0; i < r->len; i++)
    if (r->data[i] >= 'A' && r->data[i] <= 'Z') r->data[i] += 32;
  return r;
}
tystr *ty_str_trim(tystr *s) {
  if (!s) return NULL;
  int64_t a = 0, b = s->len;
  while (a < b && (unsigned char)s->data[a] <= ' ') a++;
  while (b > a && (unsigned char)s->data[b - 1] <= ' ') b--;
  return ty_str_new(s->data + a, b - a);
}
tystr *ty_str_sub(tystr *s, int32_t from, int32_t to) {
  if (!s) return NULL;
  if (from < 0) { ty_throw((tyobj *)ty_aioobe(from, s->len)); }
  if (to > s->len) { ty_throw((tyobj *)ty_aioobe(to, s->len)); }
  if (from > to) { ty_throw((tyobj *)ty_aioobe(to, s->len)); }
  return ty_str_new(s->data + from, to - from);
}
int32_t ty_str_indexof(tystr *s, tystr *sub) {
  if (!s || !sub) return -1;
  if (sub->len == 0) return 0;
  for (int64_t i = 0; i + sub->len <= s->len; i++)
    if (memcmp(s->data + i, sub->data, (size_t)sub->len) == 0) return (int32_t)i;
  return -1;
}
int32_t ty_str_charat(tystr *s, int32_t i) {
  if (!s || i < 0 || i >= s->len) ty_throw((tyobj *)ty_aioobe(i, s ? s->len : 0));
  return (unsigned char)s->data[i];
}
int32_t ty_str_contains(tystr *s, tystr *sub) { return ty_str_indexof(s, sub) >= 0; }
int32_t ty_str_starts(tystr *s, tystr *p) {
  if (!s || !p) return 0;
  return p->len <= s->len && memcmp(s->data, p->data, (size_t)p->len) == 0;
}
int32_t ty_str_ends(tystr *s, tystr *p) {
  if (!s || !p) return 0;
  return p->len <= s->len && memcmp(s->data + s->len - p->len, p->data, (size_t)p->len) == 0;
}
tystr *ty_str_replace(tystr *s, uint16_t a, uint16_t b) {
  if (!s) return NULL;
  tystr *r = ty_str_new(s->data, s->len);
  for (int64_t i = 0; i < r->len; i++)
    if ((unsigned char)r->data[i] == (a & 0xFF)) r->data[i] = (char)b;
  return r;
}
int32_t ty_str_isempty(tystr *s) { return !s || s->len == 0; }
int32_t ty_str_toint(tystr *s) { return s ? (int32_t)strtoll(s->data, NULL, 10) : 0; }

/* ------------------------------------------------------------------ arrays */

tyarr *ty_array_new(int64_t len, int64_t elemsize) { return ty_alloc_arr(len, (size_t)elemsize); }
int64_t ty_array_len(tyarr *a) { return a ? a->len : 0; }
tyarr *ty_array_clone(tyarr *a, int64_t elemsize) {
  tyarr *r = ty_alloc_arr(a->len, (size_t)elemsize);
  memcpy(r->data, a->data, (size_t)(a->len * elemsize));
  return r;
}
void *ty_arr_ptr(tyarr *a, int64_t i) {
  if (!a || i < 0 || i >= a->len) ty_throw((tyobj *)ty_aioobe(i, a ? a->len : 0));
  return (char *)a->data + (size_t)i * a->esize;
}
void *ty_arr_slot_ref(tyarr *a, int64_t i) {
  if (!a || i < 0 || i >= a->len) ty_throw((tyobj *)ty_aioobe(i, a ? a->len : 0));
  return (char *)a->data + (size_t)i * 8;
}
void *ty_arr_ref(tyarr *a, int64_t i) { return *(void **)ty_arr_slot_ref(a, i); }

void ty_array_store_ref(tyarr *a, int64_t i, void *v) {
  if (!a || i < 0 || i >= a->len) ty_throw((tyobj *)ty_aioobe(i, a ? a->len : 0));
  ((void **)a->data)[i] = v;
}

/* ------------------------------------------------------------------ boxing */

#define DEFBOX(NAME, IDX, CT, JT, CONV)                                     \
  typedef struct NAME##Box { tyobj obj; JT v; } NAME##Box;             \
  void *ty_box_##NAME(JT v) {                                          \
    NAME##Box *b = (NAME##Box *)ty_alloc(sizeof(NAME##Box));           \
    b->obj.cls = TY_BOX[IDX];                                          \
    b->v = v;                                                          \
    return b;                                                          \
  }                                                                    \
  JT ty_unbox_##NAME(void *o) {                                        \
    if (!o) ty_npe();                                                  \
    return ((NAME##Box *)o)->v;                                        \
  }

DEFBOX(int, 5, tyint, int32_t, )
DEFBOX(long, 6, tylong, int64_t, )
DEFBOX(short, 3, tyshort, int16_t, )
DEFBOX(byte, 2, tybyte, int8_t, )
DEFBOX(char, 4, tychar, uint16_t, )
DEFBOX(bool, 1, tybool, int32_t, )

void *ty_box_double(double v) {
  tydoublebox *b = (tydoublebox *)ty_alloc(sizeof(tydoublebox));
  b->obj.cls = TY_BOX[8];
  b->v = v;
  return b;
}
double ty_unbox_double(void *o) {
  if (!o) ty_npe();
  return ((tydoublebox *)o)->v;
}
void *ty_box_float(float v) {
  tyfloatbox *b = (tyfloatbox *)ty_alloc(sizeof(tyfloatbox));
  b->obj.cls = TY_BOX[7];
  b->v = v;
  return b;
}
float ty_unbox_float(void *o) {
  if (!o) ty_npe();
  return ((tyfloatbox *)o)->v;
}

/* ------------------------------------------------------------------ output */

void ty_print_str(tystr *s) { if (s) fwrite(s->data, 1, (size_t)s->len, stdout); else fputs("null", stdout); }
void ty_println_str(tystr *s) { ty_print_str(s); putchar('\n'); }
void ty_print_int(int64_t v) { printf("%lld", (long long)v); }
void ty_println_int(int64_t v) { printf("%lld\n", (long long)v); }
void ty_print_double(double v) { printf("%.17g", v); }
void ty_println_double(double v) { printf("%.17g\n", v); }
void ty_print_char(uint16_t c) {
  if (c < 0x80) putchar((int)c);
  else fputs(ty_str_of_char(c)->data, stdout);
}
void ty_println_char(uint16_t c) { ty_print_char(c); putchar('\n'); }
void ty_print_bool(int32_t v) { fputs(v ? "true" : "false", stdout); }
void ty_println_bool(int32_t v) { fputs(v ? "true\n" : "false\n", stdout); }
void ty_print_obj(void *o) { ty_print_str(ty_str_of_obj(o)); }
void ty_println_obj(void *o) { ty_print_obj(o); putchar('\n'); }
void ty_println_void(void) { putchar('\n'); }

/* ------------------------------------------------------------------ sync */

void ty_sync_enter(void *lock) { (void)lock; }
void ty_sync_exit(void *lock) { (void)lock; }

/* ------------------------------------------------------------------ int ops */

int32_t ty_div_int(int32_t a, int32_t b) {
  if (b == 0) ty_throw((tyobj *)ty_arith("/ by zero"));
  if (b == -1 && a == INT32_MIN) return INT32_MIN;
  return a / b;
}
int64_t ty_div_long(int64_t a, int64_t b) {
  if (b == 0) ty_throw((tyobj *)ty_arith("/ by zero"));
  if (b == -1 && a == INT64_MIN) return INT64_MIN;
  return a / b;
}
int32_t ty_rem_int(int32_t a, int32_t b) {
  if (b == 0) ty_throw((tyobj *)ty_arith("/ by zero"));
  if (b == -1) return 0;
  return a % b;
}
int64_t ty_rem_long(int64_t a, int64_t b) {
  if (b == 0) ty_throw((tyobj *)ty_arith("/ by zero"));
  if (b == -1) return 0;
  return a % b;
}

void ty_init(void) { ty_gc_init(); }
