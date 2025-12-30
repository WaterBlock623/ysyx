#include "Vtop.h"
#include "verilated.h"
#include "verilated_fst_c.h"
#include <nvboard.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

#define TO_STR(a) #a

void nvboard_bind_all_pins(TOP_NAME* top);

VerilatedContext* contextp = NULL;
TOP_NAME* top = NULL;
VerilatedFstC* tfp = NULL;

void sim_init(int argc, char** argv)
{
    contextp = new VerilatedContext;
    contextp->commandArgs(argc, argv);
    top = new Vtop{contextp};
	nvboard_bind_all_pins(top);
	nvboard_init();
	Verilated::traceEverOn(true);
	tfp = new VerilatedFstC;
	top->trace(tfp, 99);
	tfp->open("./build/obj_dir/wave/sim.fst");
}

void sim_close(void)
{
	tfp->close();
    delete top;
    delete contextp;
	nvboard_quit();
}

void single_cycle(void)
{
	top->clk = 0; top->eval();
	top->clk = 1; top->eval();
}

void reset(int n) {
	top->rst = 1;
	while (n-- > 0) single_cycle();
	top->rst = 0;
}

int main(int argc, char** argv) {
	int sim_time = -1;
	sim_init(argc, argv);
	reset(10);
    while ((contextp->time() < sim_time | sim_time == -1) && !contextp->gotFinish()) {
		contextp->timeInc(1);
		nvboard_update();
		single_cycle();
		tfp->dump(contextp->time());
	}
	sim_close();
    return 0;
}
