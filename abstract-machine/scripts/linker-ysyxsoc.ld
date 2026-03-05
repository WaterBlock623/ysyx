ENTRY(_start)

PHDRS { text PT_LOAD; data PT_LOAD; }

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
REGION_ALIAS("REGION_L_BSS", ROM);

SECTIONS {
  .text : {
    *(entry)
    *(.text*)
  } >REGION_V_TEXT AT>REGION_L_TEXT :text

  .rodata : {
    *(.rodata*)
  } >REGION_V_RODATA AT>REGION_L_RODATA

  .data : {
    _data_start = .;
    *(.data*)
    _data_end = .;
  } >REGION_V_DATA AT>REGION_L_DATA :data
  _data_size = SIZEOF(.data);
  _data_load_start = LOADADDR(.data);

  .bss : {
	  _bss_start = .;
    *(.bss*)
    *(.sbss*)
    *(.scommon)
    _bss_end = .;
  } >REGION_V_BSS AT>REGION_L_BSS
  _bss_size = SIZEOF(.bss);

  . = ALIGN(16);
  _heap_start = .;
  . = (ORIGIN(SRAM) + LENGTH(SRAM)) & ~(16 - 1);
  _stack_pointer = .;

  /* _stack_top = ALIGN(0x1000); */
  /* . = _stack_top + 0x8000; */
  /* _stack_pointer = .; */
  /* _heap_start = ALIGN(0x1000); */
}
