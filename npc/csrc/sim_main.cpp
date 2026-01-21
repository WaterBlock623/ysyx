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

uint32_t M[1 << 22] = {
	0x00a00093,
	0x00508113,
	0xff410193
};
uint32_t pmem_read(uint32_t addr) {
	return M[addr >> 2];
}

void single_cycle(void)
{
	top->clock = 0; top->eval();
	contextp->timeInc(1);

	top->io_lsuIn_rData_0 = pmem_read(top->io_ifuOut_memRAddr);
	printf("%0#8x\n", top->io_ifuOut_memRAddr);

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
	top->io_lsuIn_rData_1 = 0;
	top->reset = 1;
	while (n-- > 0) single_cycle();
	top->reset = 0;
}

int main(int argc, char** argv) {
	//int sim_time = 2 * 25000000;
	int sim_time = 50;
	sim_init(argc, argv);
	reset(10);
	int i = 0;
    while ((i < sim_time | sim_time == -1) && !contextp->gotFinish()) {
#ifndef NO_NVBOARD
		nvboard_update();
#endif
		single_cycle();
		i++;
	}
	sim_close();
    return 0;
}
