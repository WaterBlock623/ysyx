#include "verilated.h"
#include "verilated_fst_c.h"
#include <cstdint>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <time.h>
#include <sys/time.h>

#ifndef NO_NVBOARD
#include <nvboard.h>
#endif

//#define SIM_DEBUG

#define DEVICE_BASE 0x10000000
#define MMIO_BASE 0x10000000

#define SERIAL_PORT     (DEVICE_BASE + 0x0000000)
//#define VGACTL_ADDR     (DEVICE_BASE + 0x0000100)
//#define AUDIO_ADDR      (DEVICE_BASE + 0x0000200)
//#define DISK_ADDR       (DEVICE_BASE + 0x0000300)
//#define KBD_ADDR        (DEVICE_BASE + 0x0000500)
#define RTC_ADDR        (DEVICE_BASE + 0x0000600)
#define TIME_ADDR        (DEVICE_BASE + 0x0000700)
//#define FB_ADDR         (MMIO_BASE   + 0x1000000)
//#define AUDIO_SBUF_ADDR (MMIO_BASE   + 0x1200000)

#define SEC_ADDR TIME_ADDR
#define MIN_ADDR (TIME_ADDR + 4u)
#define HOUR_ADDR (TIME_ADDR + 8u)
#define DAY_ADDR (TIME_ADDR + 12u)
#define MON_ADDR (TIME_ADDR + 16u)
#define YEAR_ADDR (TIME_ADDR + 20u)

int stop_flag = 0;
int32_t ret_val;
uint32_t M[1 << 22];

static struct timeval boot_time = {};

static struct tm *get_rtc(void) {
  time_t t = time(NULL);
  return localtime(&t);
}

static uint64_t get_uptime(void) {
  struct timeval now;
  gettimeofday(&now, NULL);
  uint64_t seconds = now.tv_sec - boot_time.tv_sec;
  uint64_t useconds = now.tv_usec - boot_time.tv_usec;
  return seconds * 1000000 + (useconds + 500);
}

// DIP-C
extern "C" uint32_t pmem_read(uint32_t raddr) {
  switch (raddr) {
    case RTC_ADDR:
      return (uint32_t)get_uptime();
    case RTC_ADDR + 4u:
      return (uint32_t)(get_uptime() >> 32);
    case SEC_ADDR:
      return get_rtc()->tm_sec;
    case MIN_ADDR:
      return get_rtc()->tm_min;
    case HOUR_ADDR:
      return get_rtc()->tm_hour;
    case DAY_ADDR:
      return get_rtc()->tm_mday;
    case MON_ADDR:
      return get_rtc()->tm_mon + 1;
    case YEAR_ADDR:
      return get_rtc()->tm_year + 1900;
  }
	if (raddr >= 0x80000000) {
		raddr -= 0x80000000;
		return M[raddr >> 2];
	} else {	
#ifdef SIM_DEBUG
		printf("[npc] Invalid rAddr: %#.8x\n", raddr);
#endif
		return 0;
	}
}

extern "C" void pmem_write(uint32_t waddr, uint32_t wdata, unsigned char wmask) {
#ifdef SIM_DEBUG
	printf("[npc] wAddr: %#.8x  data: %#.8x  mask: %#.8x\n", waddr, wdata, wmask);
#endif
  switch (waddr) {
    case SERIAL_PORT:
      putchar(wdata);
      return;
  }
	waddr -= 0x80000000;
	uint32_t mask = 0u;
	int i;
	for (i = 0; i < 4; i++) {
		if (wmask & (1u << i))
			mask |= 0xff << (i * 8);
	}
//	printf("write data: %#.8x\n", wdata & mask);
	M[waddr >> 2] = (M[waddr >> 2] & ~mask) | (wdata & mask);
//	printf("mem: %#.8x\n", M[10]);
}

extern "C" void check_ebreak(int is_ebreak) {
//	printf("is_ebreak: %d\n", is_ebreak);
	stop_flag = is_ebreak;
	if (is_ebreak)
		printf("[npc] stop by ebreak\n");
}

extern "C" void get_ret(uint32_t a0) {
	ret_val = a0;	
}

void nvboard_bind_all_pins(TOP_NAME* top);

VerilatedContext* contextp = NULL;
TOP_NAME* top = NULL;
VerilatedFstC* tfp = NULL;

void sim_init(int argc, char** argv)
{
    contextp = new VerilatedContext;
    contextp->commandArgs(argc, argv);
    top = new TOP_NAME{contextp};
#ifndef NO_NVBOARD
	nvboard_bind_all_pins(top);
	nvboard_init();
#endif
#ifdef ENAWAVE
	Verilated::traceEverOn(true);
	tfp = new VerilatedFstC;
	top->trace(tfp, 99);
	tfp->open("./build/obj_dir/wave/sim.fst");
//	tfp->open("sim.fst");
#endif
  gettimeofday(&boot_time, NULL);
}

void sim_close(void)
{
#ifdef ENAWAVE
	tfp->close();
#endif
    delete top;
    delete contextp;
#ifndef NO_NVBOARD
	nvboard_quit();
#endif
}

void single_cycle(void)
{
	top->clock = 0; top->eval();
	contextp->timeInc(1);

#ifdef ENAWAVE
	tfp->dump(contextp->time());
#endif
	top->clock = 1; top->eval();
	contextp->timeInc(1);
#ifdef ENAWAVE
	tfp->dump(contextp->time());
#endif
}

void reset(int n) {
	top->reset = 1;
	while (n-- > 0) single_cycle();
	top->reset = 0;
}

void load_bin(const char *path) {
	printf("[npc] Load bin: %s\n", path);
	FILE *bin = fopen(path, "r");
	assert(bin);
	size_t size = fread(M, 1, sizeof(M), bin);
	if (size == sizeof(M))
		printf("[npc] Warning: M is full\n");
	else
		printf("""[npc] Load bin successful: %lu bytes\n", size);
}

void check_ret_val(void) {
	if (ret_val == 0) {
		printf("[npc] \033[32m[HIT GOOD TRAP]\033[0m\n");
	} else {
		printf("[npc] \033[31m[HIT BAD TRAP]\033[0m\n");
	}
}

int main(int argc, char** argv) {
	if (argc > 1)
		load_bin(argv[1]);
//	M[138] = 0x00100073;
//	M[1160] = 0x00100073;
	//int sim_time = 2 * 25000000;
	int sim_time = -1;
	sim_init(argc, argv);
	reset(10);
	int i = 0;
    while ((i < sim_time | sim_time == -1) && !contextp->gotFinish() && !stop_flag) {
//		if (i % 100 == 0)
//			printf("[npc] cycles: %d\n", i);
#ifndef NO_NVBOARD
		nvboard_update();
#endif
		single_cycle();
		i++;
	}
	sim_close();
	printf("[npc] Cycles: %d\n", i);
	check_ret_val();
    return ret_val;
}
