/***************************************************************************************
* Copyright (c) 2014-2024 Zihao Yu, Nanjing University
*
* NEMU is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

#include <sys/cdefs.h>

void single_cycle(void);
void sync_npc_gpr(void);

__BEGIN_DECLS

#include "common.h"
#include "difftest-def.h"
#include "macro.h"
#include <cpu/cpu.h>
#include <cpu/ifetch.h>
#include <cpu/decode.h>
#include "local-include/reg.h"
#include <unistd.h>

void sim_close(void);
void print_disassemble(Decode *);
// void restart(void);

extern CPU_state npc_state;
extern ISADecodeInfo npc_inst;
extern paddr_t npc_dnpc;
extern int npc_stop_flag;

int isa_exec_once(Decode *s) {
  static bool first = true;
  if (first) {
    // restart();
    first = false;
  } else {
    single_cycle(); 
    sleep(1);
    sync_npc_gpr();
  }
  s->isa.inst = npc_inst.inst;
  s->snpc = s->pc + 4;
  IFDEF(CONFIG_ITRACE, print_disassemble(s));
  if (npc_stop_flag != 0) {
    set_nemu_state(NEMU_END, s->pc, gpr(10));
    // sim_close();
    return 0;
  }
  s->dnpc = npc_dnpc;
  return 0;
}

__END_DECLS
