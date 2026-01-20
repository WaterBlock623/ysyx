#include "verilated.h"
#include "verilated_fst_c.h"
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

void single_cycle(void)
{
	top->clock = 0; top->eval();
#ifdef ENAWAVE
	tfp->dump(contextp->time());
	contextp->timeInc(1);
#endif
	top->clock = 1; top->eval();
#ifdef ENAWAVE
	tfp->dump(contextp->time());
	contextp->timeInc(1);
#endif
}

void reset(int n) {
	top->reset = 1;
	while (n-- > 0) single_cycle();
	top->reset = 0;
}

int main(int argc, char** argv) {
	//int sim_time = 2 * 50000000;
	int sim_time = 100;
	sim_init(argc, argv);
	reset(10);
    while ((contextp->time() < sim_time | sim_time == -1) && !contextp->gotFinish()) {
#ifndef NO_NVBOARD
		nvboard_update();
#endif
		single_cycle();
	}
	sim_close();
    return 0;
}
