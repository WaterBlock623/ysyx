MILL = $(WORK_DIR)/mill

PRJ = playground

test:
	$(MILL) -i $(PRJ).test

verilog:
	$(call git_commit, "generate verilog")
	$(MILL) -i $(PRJ).runMain $(PACKAGE_NAME).Elaborate --target-dir $(BUILD_DIR)
	-$(MAKE) lint

help:
	$(MILL) -i $(PRJ).runMain $(PACKAGE_NAME).Elaborate --help

reformat:
	$(MILL) -i __.reformat

checkformat:
	$(MILL) -i __.checkFormat

bsp:
	$(MILL) -i mill.bsp.BSP/install

idea:
	$(MILL) -i mill.idea.GenIdea/idea

clean:
	-rm -rf $(BUILD_DIR)

.PHONY: test verilog help reformat checkformat clean