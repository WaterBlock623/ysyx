#include <am.h>
#include <nemu.h>
#include <stdint.h>

#define SYNC_ADDR (VGACTL_ADDR + 4)

void __am_gpu_init() {
}

void __am_gpu_config(AM_GPU_CONFIG_T *cfg) {
  uint32_t data_raw = inl(VGACTL_ADDR);
  int width = data_raw >> 16;
  int height = data_raw & ((1 << 16) - 1);
  *cfg = (AM_GPU_CONFIG_T) {
    .present = true, .has_accel = false,
    .width = width, .height = height,
    .vmemsz = width * height * sizeof(uint32_t)
  };
}

void __am_gpu_fbdraw(AM_GPU_FBDRAW_T *ctl) {
  if (ctl->sync) {
    outl(SYNC_ADDR, 1);
  }
  int width = inl(VGACTL_ADDR) >> 16;
  int i, j;
  for (i = 0; i < ctl->h; i++) {
    for (j = 0; j < ctl->w; j++) {
      uintptr_t addr = FB_ADDR + 
        (ctl->y + i) * width * sizeof(uint32_t) + 
        (ctl->x + j) * sizeof(uint32_t);
      outl(addr, ((uint32_t *)ctl->pixels)[i * ctl->w + j]);
    }
  }
}

void __am_gpu_status(AM_GPU_STATUS_T *status) {
  status->ready = true;
}
