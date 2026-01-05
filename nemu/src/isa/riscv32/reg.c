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

#include <errno.h>
#include <isa.h>
#include <stdio.h>
#include "local-include/reg.h"
#include "common.h"
#include "debug.h"

const char *regs[] = {
  "$0", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
  "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
  "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
  "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
};

void isa_reg_display() {
  int i;
  for (i = 0; i < sizeof(cpu.gpr) / sizeof(cpu.gpr[0]); i++) {
    if (cpu.gpr[i])
      printf("%2d (%3s)  0x%.8x\n", i, regs[i], cpu.gpr[i]);
    else
      printf("%2d (%3s)  \033[2m0x%.8x\033[0m\n", i, regs[i], cpu.gpr[i]);
  } 
  printf("\n");
}

word_t isa_reg_str2val(const char *s, bool *success) {
  int gpr_max = sizeof(cpu.gpr) / sizeof(cpu.gpr[0]);
  int i;
  for (i = 0; i < gpr_max; i++) {
    if (strcmp(s + 1, regs[i])) {
      Log("read: Reg %d", i);
      return cpu.gpr[i];
    }
  }
  char *endptr = NULL;
  errno = 0;
  word_t val = strtol(s + 1, &endptr, 10);
  Log("read: Reg %d", val);
  if (errno != 0) {
    perror("");
    Log("reg name parse error");
    *success = false;
    return 0;
  }
  if (*endptr != '\0') {
    Log("exist invalid str in reg name");
  }
  if (val >= 0 && val < gpr_max) {
    return cpu.gpr[val];
  } else {
    *success = false;
    return 0;
  }
}
