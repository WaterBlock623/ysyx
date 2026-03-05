#include <utils.h>
#include <device/map.h>

static uint8_t *sram_base = NULL;

static inline void mrom_io_handler(uint32_t offset, int len, bool is_write) {}

void init_mrom(device_init_param_t *param) {
  sram_base = new_space(CONFIG_SRAM_SIZE);
  IOMap *map = add_mmio_map("sram", CONFIG_SRAM_MMIO, mrom_base, CONFIG_SRAM_SIZE, mrom_io_handler, false);
  if (param->sram_img) {
    device_load_img(map, param->sram_img);
  }
}
