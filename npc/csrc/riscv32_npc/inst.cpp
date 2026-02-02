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
void sim_close(void);

__BEGIN_DECLS

#include "common.h"
#include "difftest-def.h"
#include "macro.h"
#include <cpu/cpu.h>
#include <cpu/ifetch.h>
#include <cpu/decode.h>

void print_disassemble(Decode *);

extern CPU_state npc_state;
extern ISADecodeInfo npc_inst;
extern paddr_t npc_dnpc;

int isa_exec_once(Decode *s) {
  s->isa.inst = npc_inst.inst;
  s->snpc = s->pc + 4;
  s->dnpc = npc_dnpc;
  Log(FMT_PADDR, s->isa.inst);
  IFDEF(CONFIG_ITRACE, print_disassemble(s));
  single_cycle(); 
  sync_npc_gpr();
  if (s->pc >= 0x80000010) {
    sim_close();
    nemu_state.state = NEMU_END;
  }
  return 0;
}

__END_DECLS
