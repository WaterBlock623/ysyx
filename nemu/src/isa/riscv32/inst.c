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

#include "common.h"
#include "debug.h"
#include "difftest-def.h"
#include "local-include/reg.h"
#include "macro.h"
#include <cpu/cpu.h>
#include <cpu/ifetch.h>
#include <cpu/decode.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>

#define R(i) gpr(i)
#define Mr vaddr_read
#define Mw vaddr_write

#define IS_ALIGNED(addr, nbyte) (((addr) % (nbyte)) == 0)

#define MEM_READ_SEXT(len) do { \
        vaddr_t addr = src1 + imm; \
		    if (IS_ALIGNED(addr, (len))) R(rd) = SEXT(Mr(src1 + imm, (len)), (len) * 8); \
        else s->dnpc = isa_raise_intr(4, s->pc); \
      } while(0)

#define MEM_READ_ZEXT(len) do { \
        vaddr_t addr = src1 + imm; \
		    if (IS_ALIGNED(addr, (len))) R(rd) = Mr(src1 + imm, (len)); \
        else s->dnpc = isa_raise_intr(4, s->pc); \
      } while(0)

#define MEM_WRITE(len) do { \
        vaddr_t addr = src1 + imm; \
		    if (IS_ALIGNED(addr, (len))) Mw(src1 + imm, (len), src2); \
        else s->dnpc = isa_raise_intr(6, s->pc); \
      } while(0)


void ftrace(int rd, int rs1, paddr_t pc, paddr_t dnpc);

enum {
  TYPE_I, TYPE_U, TYPE_S, TYPE_J, TYPE_B, TYPE_R, TYPE_ZICSRR, TYPE_ZICSRI, 
  TYPE_N, // none
};

#define src1R() do { *src1 = R(*rs1); } while (0)
#define src2R() do { *src2 = R(*rs2); } while (0)
#define immI() do { *imm = SEXT(BITS(i, 31, 20), 12); } while(0)
#define immU() do { *imm = SEXT(BITS(i, 31, 12), 20) << 12; } while(0)
#define immS() do { *imm = (SEXT(BITS(i, 31, 25), 7) << 5) | BITS(i, 11, 7); } while(0)
#define immJ() do { *imm = SEXT((BITS(i, 31, 31) << 20) | \
                                (BITS(i, 19, 12) << 12) | \
                                (BITS(i, 20, 20) << 11) | \
                                (BITS(i, 30, 21) << 1), 21); } while(0)
#define immB() do { *imm = SEXT(BITS(i, 31, 31) << 12 | \
								BITS(i, 7, 7) << 11 | \
								BITS(i, 30, 25) << 5 | \
								BITS(i, 11, 8) << 1, 13); } while(0)
#define immZICSRI() do { *imm = BITS(i, 4, 0); } while(0)
#define csrZICSR() do { *csr = BITS(i, 31, 20); } while(0)

static void decode_operand(Decode *s, int *rd, int *rs1, int *rs2, word_t *src1, word_t *src2, word_t *imm, word_t *csr, int type) {
  uint32_t i = s->isa.inst;
  *rs1 = BITS(i, 19, 15);
  *rs2 = BITS(i, 24, 20);
  *rd  = BITS(i, 11, 7);
  switch (type) {
    case TYPE_I: src1R();          immI(); break;
    case TYPE_U:                   immU(); break;
    case TYPE_S: src1R(); src2R(); immS(); break;
	  case TYPE_J:			             immJ(); break;
	  case TYPE_B: src1R(); src2R(); immB(); break;
	  case TYPE_R: src1R(); src2R();		     break;
	  case TYPE_ZICSRR: src1R(); csrZICSR(); break;
	  case TYPE_ZICSRI: immZICSRI(); csrZICSR(); break;
    case TYPE_N:                           break;
    default: panic("unsupported type = %d", type);
  }
}

static int decode_exec(Decode *s) {
  s->dnpc = s->snpc;

#define INSTPAT_INST(s) ((s)->isa.inst)
#define INSTPAT_MATCH(s, name, type, ... /* execute body */ ) { \
  int rd = 0, rs1 = 0, rs2 = 0; \
  word_t src1 = 0, src2 = 0, imm = 0, csr = 0; \
  decode_operand(s, &rd, &rs1, &rs2, &src1, &src2, &imm, &csr, concat(TYPE_, type)); \
  __VA_ARGS__ ; \
}
#define SIGN(x) ({word_t _us = (x); sword_t _s; memcpy(&_s, &_us, sizeof(_s)); _s;})
#define SHIFT_RA(n, b) ((n) >> 31 ? (n) >> BITS((b), 4, 0) | \
								~BITMASK(32 - BITS((b), 4, 0)) : \
								(n) >> BITS((b), 4, 0))
#define MUX_DIV_ZERO(y, normal_result, zero_result) ((y) ? (normal_result) : (zero_result))
#define MUX_DIV_OVERFLOW(x, y, normal_result, overflow_result) \
	(SIGN(x) == INT32_MIN && SIGN(y) == -1 ? (overflow_result) : (normal_result))
								

  INSTPAT_START();
  INSTPAT("0000000 00001 00000 000 00000 11100 11", ebreak , N, \
      NEMUTRAP(s->pc, R(10))); // R(10) is $a0
  INSTPAT("0000000 00000 00000 000 00000 11100 11", ecall  , N, \
      s->dnpc = isa_raise_intr(11, s->pc));
  INSTPAT("0011000 00010 00000 000 00000 11100 11", mret   , N, \
      s->dnpc = isa_ret_intr());
  INSTPAT("??????? ????? ????? 001 ????? 11100 11", csrrw  , ZICSRR, \
      word_t tmp = src1; \
      if (rd) R(rd) = csr_read(csr); \
      csr_write(csr, tmp, -1));
  INSTPAT("??????? ????? ????? 010 ????? 11100 11", csrrs  , ZICSRR, \
      word_t tmp = csr_read(csr); \
      if (rs1) csr_set(csr, src1); \
      R(rd) = tmp);
  INSTPAT("??????? ????? ????? ??? ????? 00101 11", auipc  , U, R(rd) = s->pc + imm);
  INSTPAT("??????? ????? ????? ??? ????? 01101 11", lui    , U, R(rd) = imm);
  INSTPAT("??????? ????? ????? 000 ????? 00100 11", addi   , I, R(rd) = imm + src1);
  INSTPAT("??????? ????? ????? 100 ????? 00100 11", xori   , I, R(rd) = imm ^ src1);
  INSTPAT("??????? ????? ????? 110 ????? 00100 11", ori    , I, R(rd) = imm | src1);
  INSTPAT("??????? ????? ????? 111 ????? 00100 11", andi   , I, R(rd) = imm & src1);
  INSTPAT("??????? ????? ????? 010 ????? 00100 11", slti   , I, \
		  R(rd) = SIGN(src1) < SIGN(SEXT(imm, 12)) ? 1u : 0u);
  INSTPAT("??????? ????? ????? 011 ????? 00100 11", sltiu  , I, \
		  R(rd) = src1 < imm ? 1u : 0u);
  INSTPAT("0000000 ????? ????? 000 ????? 01100 11", add    , R, R(rd) = src1 + src2);
  INSTPAT("0100000 ????? ????? 000 ????? 01100 11", sub    , R, R(rd) = src1 - src2);
  INSTPAT("0000000 ????? ????? 010 ????? 01100 11", slt    , R, \
		  R(rd) = SIGN(src1) < SIGN(src2) ? 1u : 0u);
  INSTPAT("0000000 ????? ????? 011 ????? 01100 11", sltu   , R, \
		  R(rd) = src1 < src2 ? 1u : 0u);
  INSTPAT("0000000 ????? ????? 100 ????? 01100 11", xor    , R, R(rd) = src1 ^ src2);
  INSTPAT("0000000 ????? ????? 110 ????? 01100 11", or     , R, R(rd) = src1 | src2);
  INSTPAT("0000000 ????? ????? 111 ????? 01100 11", and    , R, R(rd) = src1 & src2);
  INSTPAT("0000000 ????? ????? 001 ????? 01100 11", sll    , R, \
		  R(rd) = src1 << BITS(src2, 4, 0));
  INSTPAT("0000000 ????? ????? 101 ????? 01100 11", srl    , R, \
		  R(rd) = src1 >> BITS(src2, 4, 0));
  INSTPAT("0100000 ????? ????? 101 ????? 01100 11", sra    , R, \
		  R(rd) = SHIFT_RA(src1, src2)); 
  INSTPAT("0000000 ????? ????? 001 ????? 00100 11", slli   , I, \
		  R(rd) = src1 << BITS(imm, 4, 0));
  INSTPAT("0000000 ????? ????? 101 ????? 00100 11", srli   , I, \
		  R(rd) = src1 >> BITS(imm, 4, 0));
  INSTPAT("0100000 ????? ????? 101 ????? 00100 11", srai   , I, \
		  R(rd) = SHIFT_RA(src1, imm)); 
  INSTPAT("??????? ????? ????? 000 ????? 11000 11", beq    , B, \
		  if (src1 == src2) s->dnpc = s->pc + imm);
  INSTPAT("??????? ????? ????? 001 ????? 11000 11", bne    , B, \
		  if (src1 != src2) s->dnpc = s->pc + imm);
  INSTPAT("??????? ????? ????? 101 ????? 11000 11", bge    , B, \
		  if (SIGN(src1) >= SIGN(src2)) s->dnpc = s->pc + imm); 
  INSTPAT("??????? ????? ????? 111 ????? 11000 11", bgeu   , B, \
		  if (src1 >= src2) s->dnpc = s->pc + imm); 
  INSTPAT("??????? ????? ????? 100 ????? 11000 11", blt    , B, \
		  if (SIGN(src1) < SIGN(src2)) s->dnpc = s->pc + imm); 
  INSTPAT("??????? ????? ????? 110 ????? 11000 11", bltu   , B, \
		  if (src1 < src2) s->dnpc = s->pc + imm); 
  INSTPAT("??????? ????? ????? ??? ????? 11011 11", jal    , J, R(rd) = s->snpc; \
		  s->dnpc = s->pc + imm; \
      IFDEF(CONFIG_FTRACE, ftrace(rd, rs1, s->pc, s->dnpc)));
  INSTPAT("??????? ????? ????? 000 ????? 11001 11", jalr   , I, R(rd) = s->snpc; \
		  s->dnpc = (imm + src1) & ~1lu; \
      IFDEF(CONFIG_FTRACE, ftrace(rd, rs1, s->pc, s->dnpc)));
  INSTPAT("??????? ????? ????? 000 ????? 00000 11", lb     , I, MEM_READ_SEXT(1) );
  INSTPAT("??????? ????? ????? 100 ????? 00000 11", lbu    , I, MEM_READ_ZEXT(1) );
  INSTPAT("??????? ????? ????? 001 ????? 00000 11", lh     , I, MEM_READ_SEXT(2) );
  INSTPAT("??????? ????? ????? 101 ????? 00000 11", lhu    , I, MEM_READ_ZEXT(2) );
  INSTPAT("??????? ????? ????? 010 ????? 00000 11", lw     , I, MEM_READ_ZEXT(4) );
  INSTPAT("??????? ????? ????? 000 ????? 01000 11", sb     , S, MEM_WRITE(1) );
  INSTPAT("??????? ????? ????? 001 ????? 01000 11", sh     , S, MEM_WRITE(2) );
  INSTPAT("??????? ????? ????? 010 ????? 01000 11", sw     , S, MEM_WRITE(4) );
  INSTPAT("0000001 ????? ????? 000 ????? 01100 11", mul    , R, R(rd) = src1 * src2);
  INSTPAT("0000001 ????? ????? 001 ????? 01100 11", mulh   , R, \
		  R(rd) = ((int64_t)SIGN(src1) * (int64_t)SIGN(src2)) >> 32);
  INSTPAT("0000001 ????? ????? 010 ????? 01100 11", mulhsu , R, \
		  R(rd) = ((int64_t)SIGN(src1) * (int64_t)src2) >> 32);
  INSTPAT("0000001 ????? ????? 011 ????? 01100 11", mulhu  , R, \
		  R(rd) = ((uint64_t)src1 * (uint64_t)src2) >> 32);
  INSTPAT("0000001 ????? ????? 100 ????? 01100 11", div    , R, \
		  R(rd) = MUX_DIV_ZERO(src2, \
					MUX_DIV_OVERFLOW(src1, src2, SIGN(src1)/SIGN(src2), src1), \
					-1));
  INSTPAT("0000001 ????? ????? 101 ????? 01100 11", divu   , R, \
		  R(rd) = MUX_DIV_ZERO(src2, src1/src2, -1));
  INSTPAT("0000001 ????? ????? 110 ????? 01100 11", rem    , R, \
		  R(rd) = MUX_DIV_ZERO(src2, \
					MUX_DIV_OVERFLOW(src1, src2, SIGN(src1)%SIGN(src2), 0), \
					src1));
  INSTPAT("0000001 ????? ????? 111 ????? 01100 11", remu   , R, \
		  R(rd) = MUX_DIV_ZERO(src2, src1%src2, src1));
  INSTPAT("??????? ????? ????? 000 ????? 00011 11", fence  , N, );
  
  INSTPAT("??????? ????? ????? ??? ????? ????? ??", inv    , N, INV(s->pc));
  INSTPAT_END();

  R(0) = 0; // reset $zero to 0

  return 0;
}

void print_disassemble(Decode *);
void pctrace(Decode *);

int isa_exec_once(Decode *s) {
  s->isa.inst = inst_fetch(&s->snpc, 4);
  IFDEF(CONFIG_PCTRACE, pctrace(s));
  IFDEF(CONFIG_ITRACE, print_disassemble(s));
  return decode_exec(s);
}
