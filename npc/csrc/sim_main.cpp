#include "verilated.h"
#include "verilated_fst_c.h"
#include <nvboard.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

void nvboard_bind_all_pins(TOP_NAME* top);

VerilatedContext* contextp = NULL;
TOP_NAME* top = NULL;
VerilatedFstC* tfp = NULL;

void sim_init(int argc, char** argv)
{
    contextp = new VerilatedContext;
    contextp->commandArgs(argc, argv);
    top = new TOP_NAME{contextp};
	nvboard_bind_all_pins(top);
	nvboard_init();
#ifdef ENAWAVE
	Verilated::traceEverOn(true);
	tfp = new VerilatedFstC;
	top->trace(tfp, 99);
//	tfp->open("./build/obj_dir/wave/sim.fst");
	tfp->open("sim.fst");
#endif
}

void sim_close(void)
{
#ifdef ENAWAVE
	tfp->close();
#endif
    delete top;
    delete contextp;
	nvboard_quit();
}

void single_cycle(void)
{
	top->clock = 0; top->eval();
	top->clock = 1; top->eval();
}

void reset(int n) {
	top->reset = 1;
	while (n-- > 0) single_cycle();
	top->reset = 0;
}

int main(int argc, char** argv) {
	int sim_time = -1;
	sim_init(argc, argv);
	reset(10);
    while ((contextp->time() < sim_time | sim_time == -1) && !contextp->gotFinish()) {
#ifdef ENAWAVE
		contextp->timeInc(1);
#endif
		nvboard_update();
		single_cycle();
#ifdef ENAWAVE
		tfp->dump(contextp->time());
#endif
	}
	sim_close();
    return 0;
}
