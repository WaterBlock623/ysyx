MILL = $(WORK_DIR)/mill

PRJ = playground

test:
	$(MILL) -i $(PRJ).test

verilog:
	$(call git_commit, "generate verilog")
	$(MILL) -i $(PRJ).runMain $(PACKAGE_NAME).Elaborate --target-dir $(BUILD_DIR)
	-$(MAKE) lint

chisel_help:
	$(MILL) -i $(PRJ).runMain $(PACKAGE_NAME).Elaborate --help

reformat:
	$(MILL) -i __.reformat

checkformat:
	$(MILL) -i __.checkFormat

bsp:
	$(MILL) -i mill.bsp.BSP/install

idea:
	$(MILL) -i mill.idea.GenIdea/idea

.PHONY: test verilog help reformat checkformat bsp idea
