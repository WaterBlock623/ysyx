#include "Vtop.h"
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
    top = new Vtop{contextp};
	nvboard_bind_all_pins(top);
	nvboard_init();
	Verilated::traceEverOn(true);
	tfp = new VerilatedFstC;
	top->trace(tfp, 99);
	tfp->open(WAVE);
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
	int a = rand() & 1;
	int b = rand() & 1;
	top->a = a;
	top->b = b;
	top->eval();
	printf("a = %d, b = %d, f = %d\n", a, b, top->f);
	assert(top->f == (a ^ b));
}

int main(int argc, char** argv) {
	int sim_time = 1000;
	sim_init(argc, argv);
    while (contextp->time() < sim_time && !contextp->gotFinish()) {
		contextp->timeInc(1);
		nvboard_update();
		single_cycle();
		tfp->dump(contextp->time());
	}
	sim_close();
    return 0;
}
