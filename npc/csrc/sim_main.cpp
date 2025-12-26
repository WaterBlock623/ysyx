#include "Vtop.h"
#include "verilated.h"
#include "verilated_fst_c.h"
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

int main(int argc, char** argv) {
    VerilatedContext* contextp = new VerilatedContext;
    contextp->commandArgs(argc, argv);
    Vour* top = new Vtop{contextp};
	Verilated::traceEverOn(true);
	VerilatedFstC* tfp = new VerilatedFstC;
	topp->trace(tfp, 99);
	tfp->open("obj_dir/wave/sim.fst");
    while (contextp->time() < sim_time && !contextp->gotFinish()) {
		contextp->timeInc(1);
		int a = rand() & 1;
		int b = rand() & 1;
		top->a = a;
		top->b = b;
		top->eval();
		printf("a = %d, b = %d, f = %d\n", a, b, top->f);
		assert(top->f == (a ^ b));
		tfp->dump(contextp->time());
	}
	tfp->close();
    delete top;
    delete contextp;
    return 0;
}
