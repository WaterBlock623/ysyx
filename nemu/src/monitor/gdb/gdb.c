#include "breakpoint.h"
#include <cpu/cpu.h>
#include <errno.h>
#include <gdbstub.h>
#include <isa.h>
#include <macro.h>
#include <memory/paddr.h>

#define REG_SIZE MUXDEF(CONFIG_ISA64, 8, 4)

extern bool g_cpu_stop_flag;

gdb_action_t emu_cont(void *args) {
  cpu_exec(-1);
  if (nemu_state.state == NEMU_STOP) {
    return ACT_RESUME;
  } else {
    return ACT_SHUTDOWN;
  }
}

gdb_action_t emu_stepi(void *args) {
  cpu_exec(1);
  if (nemu_state.state == NEMU_STOP) {
    return ACT_RESUME;
  } else {
    return ACT_SHUTDOWN;
  }
}

size_t emu_get_reg_bytes(int regno) { return REG_SIZE; }

int emu_read_reg(void *args, int regno, void *value) {
  if (regno == 32) {
    memcpy(value, &cpu.pc, emu_get_reg_bytes(regno));
    return 0;
  }
  if (is_valid_reg_idx(regno)) {
    memcpy(value, cpu.gpr + regno, emu_get_reg_bytes(regno));
    return 0;
  } else {
    return EINVAL;
  }
}

int emu_write_reg(void *args, int regno, void *value) {
#ifdef CONFIG_NPC
  return EFAULT;
#else
  if (regno == MUXDEF(CONFIG_RVE, 16, 32)) {
    memcpy(&cpu.pc, value, emu_get_reg_bytes(regno));
    return 0;
  }
  if (is_valid_reg_idx(regno)) {
    memcpy(cpu.gpr + regno, value, emu_get_reg_bytes(regno));
    return 0;
  } else {
    return EINVAL;
  }
#endif
}

int emu_read_mem(void *args, size_t addr, size_t len, void *val) {
  if (try_paddr_read(addr, len, val)) {
    return 0;
  } else {
    return EFAULT;
  }
}

int emu_write_mem(void *args, size_t addr, size_t len, void *val) {
  if (try_paddr_write(addr, len, val)) {
    return 0;
  } else {
    return EFAULT;
  }
}

bool emu_set_bp(void *args, size_t addr, bp_type_t type) {
  if (type != BP_SOFTWARE) {
    return false;
  }
  return new_bp(addr);
}

bool emu_del_bp(void *args, size_t addr, bp_type_t type) {
  if (type != BP_SOFTWARE) {
    return true;
  }
  free_bp(addr);
  return true;
};

void emu_on_interrupt(void *args) { g_cpu_stop_flag = true; }

static struct target_ops emu_ops = {
    .get_reg_bytes = emu_get_reg_bytes,
    .read_reg = emu_read_reg,
    .write_reg = emu_write_reg,
    .read_mem = emu_read_mem,
    .write_mem = emu_write_mem,
    .cont = emu_cont,
    .stepi = emu_stepi,
    .set_bp = emu_set_bp,
    .del_bp = emu_del_bp,
    .on_interrupt = emu_on_interrupt,
};

static gdbstub_t gdbstub;

void init_gdb(void) {
  init_bp_pool();
  Assert(gdbstub_init(&gdbstub, &emu_ops,
                      (arch_info_t){
                          .smp = 1,
                          .reg_num = MUXDEF(CONFIG_RVE, 17, 33),
#ifndef CONFIG_ISA64
                          .target_desc = TARGET_RV32,
#else
                          .target_desc = TARGET_RV64,
#endif
                      },
                      "127.0.0.1:1234"),
         "Fail to create socket.\n");
}

int gdb_mainloop(void) {
  if (!gdbstub_run(&gdbstub, NULL)) {
    fprintf(stderr, "Fail to run in debug mode.\n");
    return -1;
  }
  gdbstub_close(&gdbstub);
  return 0;
}
