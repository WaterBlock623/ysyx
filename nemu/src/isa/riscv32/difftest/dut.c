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

#include <isa.h>
#include <cpu/difftest.h>
#include "../local-include/reg.h"
#include "utils.h"

bool isa_difftest_checkregs(CPU_state *ref_r, vaddr_t pc) {
  int i;
  for (i = 0; i < LENGTH(cpu.gpr); i++) {
    if (gpr(i) != ref_r->gpr[i]) {
      printf("reg %d fail: nemu: " FMT_WORD "  ref: " FMT_WORD "\n", 
          i, gpr(i), ref_r->gpr[i]);
      return false;
    }
  }
  return true;
}

void isa_difftest_attach() {
}
