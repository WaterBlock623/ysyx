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
#include "sdb.h"
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define NR_WP 32

static WP wp_pool[NR_WP] = {};
static WP *head = NULL, *free_ = NULL;

void init_wp_pool() {
  int i;
  for (i = 0; i < NR_WP; i ++) {
    wp_pool[i].NO = i;
    wp_pool[i].next = (i == NR_WP - 1 ? NULL : &wp_pool[i + 1]);
  }

  head = NULL;
  free_ = wp_pool;
}

/* TODO: Implement the functionality of watchpoint */


static WP *prev_wp(WP *wp, WP *wp_head) {
  WP *p = wp_head;
  for (; p; p = p->next) {
    if (p->next == wp)
      return p;
  }
  return NULL;
}

static void delete_wp(WP *wp, WP **wp_head){
  if (wp == *wp_head) {
    *wp_head = wp->next;
  } else {
    WP *prev = prev_wp(wp, *wp_head);
    Assert(prev, "can't find prev");
    prev->next = wp->next;
  }
  wp->next = NULL;
}

static void insert_wp(WP *wp, WP **wp_head) {
  wp->next = *wp_head;
  *wp_head = wp;
}

WP* new_wp(const char *str) {
  Assert(free_, "whatchpoint is not enough");
  WP *wp = free_;
  wp->str = malloc(strlen(str) + 1);
  Assert(wp->str, "malloc error");
  strcpy(wp->str, str);
  delete_wp(wp, &free_);
  insert_wp(wp, &head);
  return wp;
}

void free_wp(WP *wp) {
  free(wp->str);
  wp->str = NULL;
  delete_wp(wp, &head);
  insert_wp(wp, &free_);
}

WP *gethead_wp(void) {
  return head;
}

void traverse_wp(int (*cmd)(WP *, void *), void *arg) {
  WP *p;
  for (p = head; p; p = p->next) {
    if (cmd(p, arg)) {
      return;
    }
  }
}

static int print_wp_(WP *wp, void *arg) {
  printf("%d: val=%u\t%s\n", wp->NO, wp->val, wp->str);
  return 0;
}

void print_wp(void) {
  traverse_wp(print_wp_, NULL);
}

static int free_wp_by_no_(WP *wp, void *no) {
  if (wp->NO == *(int *)no) {
    free_wp(wp);
    return 1;
  }
  return 0;
}

void free_wp_by_no(int no) {
  traverse_wp(free_wp_by_no_, &no);
}
