#include <am.h>
#include <klib-macros.h>
#include <klib.h>
#include <riscv/riscv.h>

static __attribute__((always_inline)) inline
void *_memset(void *s, int c, size_t n) {
  int i;
  for (i = 0; i < n; i++) {
    ((unsigned char *)s)[i] = (unsigned char)c; 
  }  
  return s;
}

static __attribute__((always_inline)) inline
void *_mempcpy(void *out, const void *in, size_t n) {
  int i;
  for (i = 0; i < n; i++) {
    ((unsigned char *)out)[i] = ((unsigned char *)in)[i];
  }
  return (unsigned char *)out + n;
}

static __attribute__((always_inline)) inline
void *_memcpy(void *out, const void *in, size_t n) {
  _mempcpy(out, in, n);
  return out;
}

extern char _data_load_start[], _data_start[], _data_end[], _data_size[];
extern char _bss_start[], _bss_end[], _bss_size[];

void _trm_init(void);

__attribute__((section(".bootloader")))
void  _bootloader(void) {
  _memcpy(_data_start, _data_load_start, (size_t)_data_size); 
  _memset(_bss_start, 0, (size_t)_bss_size);

  _trm_init();
}
