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

word_t isa_raise_intr(word_t NO, vaddr_t epc) {
  /* TODO: Trigger an interrupt/exception with ``NO''.
   * Then return the address of the interrupt/exception vector.
   */
  cpu.csr[CSR_MSTATUS] &= ~0x80;
  cpu.csr[CSR_MSTATUS] |= (cpu.csr[CSR_MSTATUS] & 0x8) << 4;
  cpu.csr[CSR_MSTATUS] &= ~0x8;
  cpu.csr[CSR_MEPC] = epc;
  cpu.csr[CSR_MCAUSE] = NO;
  return cpu.csr[CSR_MTVEC];
}

static word_t csr_addrs[NR_CSR] = {
  0x300, // mstatus
  0x305, // mtvec
  0x341, // mepc
  0x342, // mcause
};

static int find_csr(word_t csr_addr) {
  int i;
  for (i = 0; i < NR_CSR; i++) {
    if (csr_addr == csr_addrs[i]) {
      return i;
    }
  }
  return -1;
}

word_t csr_read(word_t csr_addr) {
  int idx = find_csr(csr_addr);
  Assert(idx >= 0, "Unsupported csr_addr: " FMT_WORD, csr_addr);
  return cpu.csr[idx];
}

void csr_write(word_t csr_addr, word_t wdata, word_t wmask) {
  int idx = find_csr(csr_addr);
  Assert(idx >= 0, "Unsupported csr_addr: " FMT_WORD, csr_addr);
  cpu.csr[idx] &= ~wmask;
  cpu.csr[idx] |= wdata & wmask;
}

void csr_set(word_t csr_addr, word_t wmask) {
  csr_write(csr_addr, -1, wmask);
}

void csr_clear(word_t csr_addr, word_t wmask) {
  csr_write(csr_addr, 0, wmask);
}

word_t isa_query_intr() {
  return INTR_EMPTY;
}
