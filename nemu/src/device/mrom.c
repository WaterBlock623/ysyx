#include <utils.h>
#include <device/map.h>

extern char *rom_file;
static uint8_t *mrom_base = NULL;

static inline void mrom_io_handler(uint32_t offset, int len, bool is_write) {}

static void load_rom(void) {
  Assert(rom_file, "--rom is not given");
  Assert(mrom_base, "mrom_base should not be NULL");

  FILE *fp = fopen(rom_file, "rb");
  Assert(fp, "Can not open '%s'", rom_file);

  fseek(fp, 0, SEEK_END);
  long size = ftell(fp);

  Log("The rom is %s, size = %ld", rom_file, size);

  fseek(fp, 0, SEEK_SET);
  int ret = fread(mrom_base, size, 1, fp);
  assert(ret == 1);

  fclose(fp);
}

void init_serial() {
  mrom_base = new_space(CONFIG_MROM_SIZE);
  add_mmio_map("mrom", CONFIG_MROM_MMIO, mrom_base, CONFIG_MROM_SIZE, mrom_io_handler, false);
  load_rom();
}
