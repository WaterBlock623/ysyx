#include "verilated.h"
#include "verilated_fst_c.h"
#include <cstdint>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

#ifndef NO_NVBOARD
#include <nvboard.h>
#endif

int stop_flag = 0;

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

uint32_t M[1 << 22];
extern "C" uint32_t pmem_read(uint32_t raddr) {
	if (raddr != 0) {
		raddr -= 0x80000000;
		return M[raddr >> 2];
	} else {		
		printf("[npc] Invalid rAddr: %#.8x\n", raddr);
		return 0;
	}
}
extern "C" void pmem_write(uint32_t waddr, uint32_t wdata, unsigned char wmask) {
//	printf("[npc] wAddr: %#.8x  data: %#.8x  mask: %#.8x\n", waddr, wdata, wmask);
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


extern "C" void check_ebreak(int is_ebreak) {
//	printf("is_ebreak: %d\n", is_ebreak);
	stop_flag = is_ebreak;
	if (is_ebreak)
		printf("[npc] stop by ebreak\n");
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

int32_t ret_val;
extern "C" void get_ret(uint32_t a0) {
	ret_val = a0;	
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
