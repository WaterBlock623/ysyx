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

#include "local-include/reg.h"
#include "verilated.h"
#include "verilated_fst_c.h"
#include <isa.h>
#include <memory/paddr.h>
#include <sys/cdefs.h>

VerilatedContext *contextp = NULL;
__TOP_NAME__ *top = NULL;
VerilatedFstC *tfp = NULL;

int npc_stop_flag = 0;
CPU_state npc_state = {};
ISADecodeInfo npc_inst = {};
paddr_t npc_dnpc;

// DIP-C
extern "C" uint32_t dpic_pmem_read(uint32_t raddr) {
  if (raddr < 0x80000000) {
    Log("Invalid raddr: %u", raddr);
    return 0;
  }
  raddr &= ~3u;
  return paddr_read(raddr, 4);
}

extern "C" void dpic_pmem_write(uint32_t waddr, uint32_t wdata,
                                unsigned char wmask) {
  waddr &= ~3u;
  wmask &= ~15u;
  Log("Front " FMT_PADDR " " FMT_PADDR " %d", waddr, wdata, wmask);
  if (wmask == 0) {
    return;
  }
  while ((wmask & 1u) == 0) {
    waddr++;
    wmask >>= 1;
  }
  int len = 0;
  while (wmask & 1u) {
    len++;
    wmask >>= 1;
  }
  Log(FMT_PADDR " " FMT_PADDR " %d", waddr, wdata, len);
  paddr_write(waddr, len, wdata);
}

extern "C" void set_debug_info(int is_ebreak, uint32_t pc, uint32_t dnpc, uint32_t inst) {
  npc_stop_flag = is_ebreak;
  npc_state.pc = pc;
  npc_dnpc = dnpc;
  npc_inst.inst = inst;
  Log("%u %u %u", is_ebreak, pc, inst);
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
    // 0x00000297, // auipc t0,0
    // 0x00028823, // sb  zero,16(t0)
    // 0x0102c503, // lbu a0,16(t0)
    // 0x00100073, // ebreak (used as nemu_trap)
    // 0xdeadbeef, // some data
    0x80000537, // lui x10, 0x80000
    0x10050513, // addi x10, x10, 256
    0x00a505b3, // add x11, x10, x10
    0x00b52023, // sw x11, 0(x10)
    0x00b54023, // sb x11, 0(x10)
    0x00052583, // lw x11, 0(x10)
    0x00054583, // lbu x11, 0(x10)
    0x00c000ef, // jal x1, 12
    0x00100073, // ebreak (should not be here)
    0x00008067, // jalr x0, 0(x1)
    0x00100073, // ebreak 
};

void sync_npc_gpr(void) {
  int i;
  for (i = 0; i < LENGTH(cpu.gpr); i++) {
    gpr(i) = npc_state.gpr[i];
  }
}

static void restart() {
  reset(20);
  cpu.pc = npc_state.pc;
  sync_npc_gpr();
}

__BEGIN_DECLS
void init_isa() {
  sim_init();
  /* Load built-in image. */
  memcpy(guest_to_host(RESET_VECTOR), img, sizeof(img));

  /* Initialize this virtual computer system. */
  restart();
}
__END_DECLS
