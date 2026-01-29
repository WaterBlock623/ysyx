#include "debug.h"
#include <SDL2/SDL_stdinc.h>
#include <common.h>
#include <elf.h>
#include <stdio.h>
#include <string.h>

typedef MUXDEF(CONFIG_ISA64, Elf64_Ehdr, Elf32_Ehdr) elf_ehdr_t;
typedef MUXDEF(CONFIG_ISA64, Elf64_Phdr, Elf32_Phdr) elf_phdr_t;
typedef MUXDEF(CONFIG_ISA64, Elf64_Shdr, Elf32_Shdr) elf_shdr_t;
typedef MUXDEF(CONFIG_ISA64, Elf64_Sym, Elf32_Sym) elf_sym_t;

#define SH_MAX 64
#define SH_NAME_MAX 128

static elf_ehdr_t eh;
static elf_shdr_t sh[SH_MAX];
static char sh_name[SH_MAX][SH_NAME_MAX];

static size_t fread_assert(void *ptr, size_t byte, FILE *stream) {
  size_t ret = fread(ptr, 1, byte, stream);
  Assert(ret == byte, "fread fail: expect %lu bytes but %lu byte", byte, ret);
  return byte;
}

void init_elf(const char *elf_file) {
  if (elf_file == NULL) {
    Log("No ELF file");
    return;
  }
  FILE *elf = fopen(elf_file, "r");
  Assert(elf, "Cant open ELF file");
  Log("ELF file: %s", elf_file);

  // ELF header
  fread_assert(&eh, sizeof(eh), elf);
  Log("ELF header: off: %u  size: %u  num: %u  strndx: %u\n", 
      eh.e_shoff, eh.e_shentsize, eh.e_shnum, eh.e_shstrndx);

  // Section header
  Assert(eh.e_shnum <= SH_MAX, "SH_MAX is not enough. At least %u", eh.e_shnum);
  int i;
  fseek(elf, eh.e_shoff, SEEK_SET);
  for (i = 0; i < eh.e_shnum; i++) {
    elf_shdr_t *s = sh + i;
    fread_assert(s, eh.e_shentsize, elf);
  }
  
  // Section header name
  word_t shstrtab = sh[eh.e_shstrndx].sh_offset;
  for (i = 0; i < eh.e_shnum; i++) {
    fseek(elf, shstrtab + sh[i].sh_name, SEEK_SET);
    fread_assert(sh_name[i], SH_NAME_MAX, elf);
    sh_name[i][SH_NAME_MAX - 1] = '\0';
    Log("Section header %d: "
        "name: %s  addr: %#x  off: %#x  size: %u  entsize: %u\n", 
        i, sh_name[i], sh[i].sh_addr, sh[i].sh_offset, 
        sh[i].sh_size, sh[i].sh_entsize);
  }
}
