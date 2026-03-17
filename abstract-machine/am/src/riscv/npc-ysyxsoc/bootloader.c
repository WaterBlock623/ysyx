#include <am.h>
#include <klib-macros.h>
#include <klib.h>
#include <riscv/riscv.h>

extern char _data_load_start[], _data_start[], _data_end[], _data_size[];
extern char _bss_start[], _bss_end[], _bss_size[];

void _trm_init(void);

void __attribute__((section(".bootloader"))) _bootloader(void) {
  memcpy(_data_start, _data_load_start, (size_t)_data_size); 
  memset(_bss_start, 0, (size_t)_bss_size);

  _trm_init();
}
