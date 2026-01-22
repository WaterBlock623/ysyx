#include "verilated.h"
#include "verilated_fst_c.h"
#include <cstdint>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

#ifndef NO_NVBOARD
#include <nvboard.h>
#endif

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
	return M[raddr >> 2];
}
extern "C" void pmem_write(uint32_t waddr, uint32_t wdata, unsigned char wmask) {
	uint32_t mask = 0u;
	int i;
	for (i = 0; i < 4; i++) {
		if (wmask & (1u << i))
			mask |= 0xff << (i * 8);
	}
	M[waddr >> 2] = wdata & mask;
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

int stop_flag = 0;

extern "C" void check_ebreak(int is_ebreak) {
//	printf("is_ebreak: %d\n", is_ebreak);
	stop_flag = is_ebreak;
}

void load_bin(const char *path) {
	FILE *bin = fopen(path, "r");
	assert(bin);
	size_t size = fread(M, 1, sizeof(M), bin);
	if (size == sizeof(M))
		printf("Warning: M is full\n");
	else
		printf("Load bin successful: %lu bytes\n", size);
}

int main(int argc, char** argv) {
	load_bin("util/bin/mem.bin");
//	M[138] = 0x00100073;
	M[1160] = 0x00100073;
	//int sim_time = 2 * 25000000;
	int sim_time = -1;
	sim_init(argc, argv);
	reset(10);
	int i = 0;
    while ((i < sim_time | sim_time == -1) && !contextp->gotFinish() && !stop_flag) {
		printf("cycles: %d\n", i);
#ifndef NO_NVBOARD
		nvboard_update();
#endif
		single_cycle();
		i++;
	}
	sim_close();
    return 0;
}
