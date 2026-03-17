ENTRY(_start)

/* PHDRS { text PT_LOAD; data PT_LOAD; } */

MEMORY {
  MROM (RX) : ORIGIN = 0x20000000, LENGTH = 4K
  FLASH (RX) : ORIGIN = 0x30000000, LENGTH = 16M
  SRAM (!R) : ORIGIN = 0x0f000000, LENGTH = 8K
  PSRAM (!R) : ORIGIN = 0x80000000, LENGTH = 4M
}

REGION_ALIAS("ROM", FLASH);
REGION_ALIAS("RAM", PSRAM);
REGION_ALIAS("FASTRAM", SRAM);

/* REGION_ALIAS("REGION_V_TEXT", ROM); */
/* REGION_ALIAS("REGION_L_TEXT", ROM); */
/**/
/* REGION_ALIAS("REGION_V_RODATA", ROM); */
/* REGION_ALIAS("REGION_L_RODATA", ROM); */
/**/
/* REGION_ALIAS("REGION_V_DATA", RAM); */
/* REGION_ALIAS("REGION_L_DATA", ROM); */
/**/
/* REGION_ALIAS("REGION_V_BSS", RAM); */
/* REGION_ALIAS("REGION_L_BSS", ROM); */

SECTIONS {
  .xiptext : {
    *(entry)
    *(.fsbl)
  } >ROM AT>ROM

  /* Fast RAM load start */
  _fastram_load_start = LOADADDR(.fasttext);
  _fastram_start = ADDR(.fasttext);
  .fasttext : {
    *(.ssbl)
  } >FASTRAM AT>ROM
  _fastram_end = .;
  _fastram_size = _fastram_end - _fastram_start;
  /* Fast RAM load end */

  /* Load start */
  _data_load_start = LOADADDR(.text);
  _data_start = ADDR(.text);
  .text : {
    *(.text*)
  } >RAM AT>ROM

  .rodata : {
    *(.rodata*)
    *(.srodata*)
  } >RAM AT>ROM

  .data : {
    *(.data*)
  } >RAM AT>ROM

  .sdata : {
    . = ALIGN(16);
    __global_pointer$ = . + 0x800;
    *(.sdata*)
  } >RAM AT>ROM
  _data_end = .;
  _data_size = _data_end - _data_start;
  /* Load end */

  /* Clear start */
	_bss_start = ADDR(.sbss);
  .sbss : {
    *(.sbss*)
    *(.scommon)
  } >RAM

  .bss : {
    *(.bss*)
    *(COMMON)
  } >RAM
  _bss_end = .;
  _bss_size = _bss_end - _bss_start;
  /* Clear end */

  . = ALIGN(16);
  _heap_start = .;
  . = (ORIGIN(RAM) + LENGTH(RAM)) & ~(16 - 1);
  _heap_end = .;

  _stack_pointer = (ORIGIN(FASTRAM) + LENGTH(FASTRAM)) & ~(16 - 1);

  /* _stack_top = ALIGN(16); */
  /* . = _stack_top + 0x1000; */
  /* _stack_pointer = .; */
  /* Clear start */
  /* _heap_start = ALIGN(16); */
  /* . = (ORIGIN(SRAM) + LENGTH(SRAM)) & ~(16 - 1); */
  /* _heap_end = .; */
}
