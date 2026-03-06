ENTRY(_start)

/* PHDRS { text PT_LOAD; data PT_LOAD; } */

MEMORY {
  MROM (RX) : ORIGIN = 0x20000000, LENGTH = 4K
  SRAM (!RX) : ORIGIN = 0x0f000000, LENGTH = 8K
}

REGION_ALIAS("ROM", MROM);
REGION_ALIAS("RAM", SRAM);

REGION_ALIAS("REGION_V_TEXT", ROM);
REGION_ALIAS("REGION_L_TEXT", ROM);

REGION_ALIAS("REGION_V_RODATA", ROM);
REGION_ALIAS("REGION_L_RODATA", ROM);

REGION_ALIAS("REGION_V_DATA", RAM);
REGION_ALIAS("REGION_L_DATA", ROM);

REGION_ALIAS("REGION_V_BSS", RAM);
/* REGION_ALIAS("REGION_L_BSS", ROM); */

SECTIONS {
  .text : {
    *(entry)
    *(.text*)
  } >REGION_V_TEXT AT>REGION_L_TEXT

  .rodata : {
    *(.rodata*)
    *(.srodata*)
  } >REGION_V_RODATA AT>REGION_L_RODATA

  /* Load start */
  _data_load_start = LOADADDR(.data);
  _data_start = ADDR(.data);
  .data : {
    *(.data*)
  } >REGION_V_DATA AT>REGION_L_DATA

  .sdata : {
    . = ALIGN(16);
    __global_pointer$ = . + 0x800;
    *(.sdata*)
  } >REGION_V_DATA AT>REGION_L_DATA
  _data_end = .;
  _data_size = _data_end - _data_start;
  /* Load end */

  /* Clear start */
	_bss_start = ADDR(.sbss);
  .sbss : {
    *(.sbss*)
    *(.scommon)
  } >REGION_V_BSS

  .bss : {
    *(.bss*)
    *(COMMON)
  } >REGION_V_BSS
  _bss_end = .;
  _bss_size = _bss_end - _bss_start;
  /* Clear end */

  . = ALIGN(16);
  _heap_start = .;
  . = (ORIGIN(SRAM) + LENGTH(SRAM)) & ~(16 - 1);
  _stack_pointer = .;
  _heap_end = .;

  /* _stack_top = ALIGN(16); */
  /* . = _stack_top + 0x1000; */
  /* _stack_pointer = .; */
  /* Clear start */
  /* _heap_start = ALIGN(16); */
  /* . = (ORIGIN(SRAM) + LENGTH(SRAM)) & ~(16 - 1); */
  /* _heap_end = .; */
}
