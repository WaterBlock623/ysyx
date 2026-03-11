#include <am.h>
#include <klib-macros.h>
#include <klib.h>
#include <riscv/riscv.h>
#include "npc.h"

int main(const char *args);

extern char _heap_start[], _heap_end[];
Area heap = RANGE(_heap_start, _heap_end);
static const char mainargs[MAINARGS_MAX_LEN] = TOSTRING(MAINARGS_PLACEHOLDER); // defined in CFLAGS

void putch(char ch) {
#define SERIAL_LSR (SERIAL_PORT + 5u)
  while (!((inb(SERIAL_LSR) >> 5) & 1u));
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
  // printf("[TRM] mvendorid: 0x%llx  marchid: %llu\n", mvendorid, marchid);
}

static void serial_init(void) {
#define SERIAL_FREQ 50 * 1000000
#define SERIAL_BAUD 115200
#define SERIAL_DL_VAL ((uint16_t)((SERIAL_FREQ) / (16 * (SERIAL_BAUD))))

#define SERIAL_DLLO (SERIAL_PORT)
#define SERIAL_DLHI (SERIAL_PORT + 1u)
#define SERIAL_LCR (SERIAL_PORT + 3u)

  setb(SERIAL_LCR, 1u << 7);
  outb(SERIAL_DLHI, (uint8_t)(SERIAL_DL_VAL >> 8));
  outb(SERIAL_DLLO, (uint8_t)SERIAL_DL_VAL);
  clearb(SERIAL_LCR, 1u << 7);
}

extern void __am_asm_trap(void);
extern char _data_load_start[], _data_start[], _data_end[], _data_size[];
extern char _bss_start[], _bss_end[], _bss_size[];
void _trm_init() {
  asm volatile("csrw mtvec, %0" : : "r"(__am_asm_trap));
  memcpy(_data_start, _data_load_start, (size_t)_data_size); 
  memset(_bss_start, 0, (size_t)_bss_size);

  serial_init();

  // put_csrid();
  int ret = main(mainargs);
  halt(ret);
}
