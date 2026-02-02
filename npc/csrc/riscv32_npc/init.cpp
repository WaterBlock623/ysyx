/***************************************************************************************
 * Copyright (c) 2014-2024 Zihao Yu, Nanjing University
 *
 * NEMU is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan
 * PSL v2. You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 *
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY
 * KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 *
 * See the Mulan PSL v2 for more details.
 ***************************************************************************************/

#include "verilated.h"
#include "verilated_fst_c.h"
#include <isa.h>
#include <memory/paddr.h>
#include <sys/cdefs.h>

VerilatedContext *contextp = NULL;
__TOP_NAME__ *top = NULL;
VerilatedFstC *tfp = NULL;

int stop_flag = 0;
CPU_state npc_state = {};
ISADecodeInfo npc_inst = {};

// DIP-C
extern "C" uint32_t dpic_pmem_read(uint32_t raddr) {
  if (raddr == 0) {
    return 0;
  }
  raddr &= ~3u;
  return paddr_read(raddr, 4);
}

extern "C" void dpic_pmem_write(uint32_t waddr, uint32_t wdata,
                                unsigned char wmask) {
  waddr &= ~3u;
  wmask &= ~15u;
  while ((wmask & 1u) == 0) {
    waddr++;
    wmask >>= 1;
  }
  int len = 0;
  while (wmask & 1u) {
    len++;
    wmask >>= 1;
  }
  paddr_write(waddr, len, wdata);
}

extern "C" void check_ebreak(int is_ebreak) {
  stop_flag = is_ebreak;
  if (is_ebreak)
    Log("[npc] stop by ebreak\n");
}

// extern "C" void get_ret(uint32_t a0) {
// 	ret_val = a0;
// }

extern "C" void set_gpr_ptr(int idx, uint32_t val) {
  if (idx >= 0 && idx < LENGTH(npc_state.gpr)) {
    npc_state.gpr[idx] = val;
  }
}

static void sim_init(void) {
  contextp = new VerilatedContext;
  top = new __TOP_NAME__{contextp};
#ifdef __ENAWAVE__
  Verilated::traceEverOn(true);
  tfp = new VerilatedFstC;
  top->trace(tfp, 99);
  tfp->open("./build/obj_dir/wave/sim.fst");
#endif
}

void single_cycle(void) {
  top->clock = 0;
  top->eval();
  contextp->timeInc(1);

#ifdef __ENAWAVE__
  tfp->dump(contextp->time());
#endif
  top->clock = 1;
  top->eval();
  contextp->timeInc(1);
#ifdef __ENAWAVE__
  tfp->dump(contextp->time());
#endif
}

static void reset(int n) {
  top->reset = 1;
  while (n-- > 0)
    single_cycle();
  top->reset = 0;
}

// this is not consistent with uint8_t
// but it is ok since we do not access the array directly
static const uint32_t img[] = {
    0x00000297, // auipc t0,0
    0x00028823, // sb  zero,16(t0)
    0x0102c503, // lbu a0,16(t0)
    0x00100073, // ebreak (used as nemu_trap)
    0xdeadbeef, // some data
};

static void restart() {
  reset(20);
  /* Set the initial program counter. */
  cpu.pc = top->rootp->Top__DOT__pcReg__DOT__pcReg;

  /* The zero register is always 0. */
  top
}

__BEGIN_DECLS
void init_isa() {
  /* Load built-in image. */
  memcpy(guest_to_host(RESET_VECTOR), img, sizeof(img));

  /* Initialize this virtual computer system. */
  restart();
}
__END_DECLS
