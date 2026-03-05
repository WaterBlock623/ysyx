#include <am.h>
#include <klib-macros.h>
#include <klib.h>
#include <riscv/riscv.h>
#include "npc.h"

int main(const char *args);

extern char _heap_start, _heap_end;
Area heap = RANGE(&_heap_start, &_heap_end);
static const char mainargs[MAINARGS_MAX_LEN] = TOSTRING(MAINARGS_PLACEHOLDER); // defined in CFLAGS

void putch(char ch) {
  outb(SERIAL_PORT, ch);
}

void halt(int code) {
  asm volatile("mv a0, %0; ebreak" : :"r"(code));
  while (1);
}

static inline void put_csrid(void) {
  unsigned long long mvendorid = 0, marchid = 0; 
  asm volatile("csrr %0, mvendorid" : "=r"(mvendorid));
  asm volatile("csrr %0, marchid" : "=r"(marchid));
  printf("[TRM] mvendorid: 0x%llx  marchid: %llu\n", mvendorid, marchid);
}

extern char _data_load_start[], _data_start[], _data_end[], _data_size[];
extern char _bss_start[], _bss_end[], _bss_size[];
void _trm_init() {
  memcpy(_data_start, _data_load_start, (size_t)_data_size); 
  memset(_bss_start, 0, (size_t)_bss_size);

  // put_csrid();
  int ret = main(mainargs);
  halt(ret);
}
