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

#include "debug.h"
#include <alloca.h>
#include <asm-generic/errno-base.h>
#include <errno.h>
#include <inttypes.h>
#include <isa.h>

/* We use the POSIX regex functions to process regular expressions.
 * Type 'man regex' for more information about POSIX regex functions.
 */
#include <regex.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

enum {
  TK_NOTYPE = 256, TK_EQ, TK_NUM10,


};

struct op_attribute {
  bool is_op;
  int precedence; // The smaller the number, the higher the priority
  bool is_right_associative;
  int unary;      // 0: Binary  1: Suffix  -1: Prefix
  uint32_t (*calc)(uint32_t, uint32_t, bool *);
};

uint32_t calc_add(uint32_t val1, uint32_t val2, bool *success) {
  return val1 + val2;
}
uint32_t calc_sub(uint32_t val1, uint32_t val2, bool *success) {
  return val1 - val2;
}
uint32_t calc_mul(uint32_t val1, uint32_t val2, bool *success) {
  return val1 * val2;
}
uint32_t calc_div(uint32_t val1, uint32_t val2, bool *success) {
  if (val2 == 0) {
    *success = false;
    return 0;
  }
  return val1 / val2;
}

static struct rule {
  const char *regex;
  int group;
  int token_type;
  struct op_attribute op;
} rules[] = {

  /* TODO: Add more rules.
   * Pay attention to the precedence level of different rules.
   */

  {"[0-9]+", 0, TK_NUM10, {false}},
  {" +", 0, TK_NOTYPE, {false}},    // spaces
  {"\\(", 0, '(', {false}},
  {"\\)", 0, ')', {false}},
  {"==", 0, TK_EQ, {false}},        // equal

  {"\\+", 0, '+', {true, 5, false, 0, calc_add}},         // plus
  {"\\-", 0, '-', {true, 5, false, 0, calc_sub}},
  {"\\*", 0, '*', {true, 4, false, 0, calc_mul}},
  {"\\/", 0, '/', {true, 4, false, 0, calc_div}},
};

#define NR_REGEX ARRLEN(rules)

static regex_t re[NR_REGEX] = {};
static int nsub_max = 0;

/* Rules are used for many times.
 * Therefore we compile them only once before any usage.
 */
void init_regex() {
  int i;
  char error_msg[128];
  int ret;

  for (i = 0; i < NR_REGEX; i ++) {
    ret = regcomp(&re[i], rules[i].regex, REG_EXTENDED);
    if (ret != 0) {
      regerror(ret, &re[i], error_msg, 128);
      panic("regex compilation failed: %s\n%s", error_msg, rules[i].regex);
    }
    if (re[i].re_nsub > nsub_max)
      nsub_max = re[i].re_nsub;
  }
}


typedef struct token {
  int type;
  struct op_attribute op;
  char *str;
} Token;

#define TOKENS_MAX_LENGTH 65536
static Token tokens[TOKENS_MAX_LENGTH] __attribute__((used)) = {};
static int nr_token __attribute__((used))  = 0;

static struct token *get_token_ptr(void) {
  Assert(nr_token < TOKENS_MAX_LENGTH, "tokens is full");
  struct token *tok_ptr = tokens + nr_token;
  nr_token++; 
  return tok_ptr;
}

static void set_token(struct token *tok_ptr, int type_idx, char *str, int str_len) {
  int tok_type = rules[type_idx].token_type;
  struct op_attribute tok_op = rules[type_idx].op;
  Assert(tok_type != TK_NOTYPE, "TK_NOTYPE shouldn't add to tokens");
  tok_ptr->type = tok_type;
  tok_ptr->op = tok_op;
  switch (tok_type) {
    case TK_NUM10: {
      char *str_ptr = malloc(str_len + 1);
      Assert(str_ptr, "malloc return NULL");
      strncpy(str_ptr, str, str_len);
      str_ptr[str_len] = '\0';
      tok_ptr->str = str_ptr;
      break;
    }
  }
}

static bool make_token(char *e) {
  int position = 0;
  int i;
  regmatch_t *pmatch = alloca((nsub_max + 1) * sizeof(regmatch_t));

  nr_token = 0;

  while (e[position] != '\0') {
    /* Try all rules one by one. */
    for (i = 0; i < NR_REGEX; i ++) {
      if (regexec(&re[i], e + position, re[i].re_nsub + 1, pmatch, 0) == 0 && pmatch[rules[i].group].rm_so == 0) {
        char *substr_start = e + position;
        int substr_len = pmatch[rules[i].group].rm_eo;

        Log("match rules[%d] = \"%s\" at position %d with len %d: %.*s",
            i, rules[i].regex, position, substr_len, substr_len, substr_start);

        position += substr_len;

        /* TODO: Now a new token is recognized with rules[i]. Add codes
         * to record the token in the array `tokens'. For certain types
         * of tokens, some extra actions should be performed.
         */
        Assert(substr_len > 0, "substr's length is 0");
        if (rules[i].token_type != TK_NOTYPE) {
          struct token *tok_ptr = get_token_ptr();
          set_token(tok_ptr, i, substr_start, substr_len);
        }
        break;
      }
    }

    if (i == NR_REGEX) {
      printf("no match at position %d\n%s\n%*.s^\n", position, e, position, "");
      return false;
    }
  }

  return true;
}

static void free_tokens(void) {
  int i;
  for (i = 0; i < nr_token; i++) {
    char *str_ptr = tokens[i].str;
    if (str_ptr) {
      free(str_ptr);
      tokens[i].str = NULL;
    }
  }
}

// RETURN: -1: invalid token
//          0: no outer parentheses
//          1: exist outer parenthese
static int check_parentheses(Token *start, Token *end) {
  if (start > end)
    return -1;
  int cnt = 0;
  bool is_outer_pair = true;
  Token *p = start;
  while(p <= end) {
    if (p->type == '(') {
      cnt++;
    } else if (p->type == ')') {
      cnt--;
    }
    if (cnt == 0 && p != end)
      is_outer_pair = false;
    p++;
  }
  if (cnt != 0) {
    return -1;
  } else if (is_outer_pair) {
    return 1;
  } else {
    return 0;
  }
}

static bool in_parentheses(Token *target, Token *end) {
  int cnt = 0;
  Token *p = target + 1;
  while (p <= end) {
    if (p->type == '(') {
      cnt++;
    } else if (p->type == ')') {
      cnt--;
    }
    if (cnt < 0)
      return true;
    p++;
  }
  return false;
}

static Token *get_main_op(Token *start, Token *end) {
  Token *main_op = NULL;
  Token *p;
  for (p = start; p <= end; p++) {
    if (!(p->op.is_op)) {
      continue;
    } else if (in_parentheses(p, end)) {
      continue;
    } else if (main_op == NULL) {
      main_op = p;
    } else if (p->op.precedence > main_op->op.precedence) {
      main_op = p;
    } else if ((p->op.precedence == main_op->op.precedence) && 
                !(p->op.is_right_associative)) {
      Assert(main_op->op.is_right_associative == p->op.is_right_associative, 
             "exist diffirent associative in the same precedence: %c %c", 
              main_op->type, p->type);
      main_op = p;
    }
  }
  return main_op;
}

static uint32_t eval(Token *start, Token *end, bool *success) {
  bool scs = true;
  if (start > end) {
    Log("invalid token");
    *success = false;
    return 0;
  } else if (start == end) {
    char *endptr = NULL;
    errno = 0;
    uint32_t val = strtol(start->str, &endptr, 10);
    if (errno == ERANGE) {
      Log("number is out of range: %s", start->str);
    } else if (errno == EINVAL) {
      Log("token is not a number: %s", start->str);
      *success = false;
      return 0;
    }
    if (*endptr != '\0') {
      Log("exist non-number token in a number string: %s", (*start).str);
    }
    return val;
  } else {
    int pair_check_result = check_parentheses(start, end);
    if (pair_check_result < 0) {
      Log("exist unmatched parentheses");
      *success = false;
      return 0;
    } else if (pair_check_result) {
      uint32_t val = eval(start + 1, end - 1, &scs);
      if (scs) {
        return val;
      } else {
        *success = false;
        return 0;
      }
    } else {
      Token *op = get_main_op(start, end);
      Assert(op, "main op not found");
      uint32_t val1 = op == start ? 0 : eval(start, op - 1, &scs);
      uint32_t val2 = op == end ? 0 : eval(op + 1, end, &scs);
      uint32_t result = 0;
      if (scs) {
        result = op->op.calc(val1, val2, &scs);
      }
      if (scs) {
        return result;
      } else {
        *success = false;
        return 0;
      }
    }
  }
}

word_t expr(char *e, bool *success) {
  if (!make_token(e)) {
    *success = false;
    return 0;
  }
  
  bool scs = true;
  uint32_t val = eval(tokens, tokens + (nr_token - 1), &scs);
  if (!scs) {
    *success = false;
    return 0;
  }

  free_tokens();

  return val;
}
