MILL = $(WORK_DIR)/mill
RSYNC = rsync
PRJ = playground

VSRC_TMP_DIR = $(BUILD_DIR)/vsrc_tmp
VSRC_DIR = $(BUILD_DIR)/vsrc

RSYNC_CMD = $(RSYNC) -rlpgoD --checksum --delete --itemize-changes \
						--omit-dir-times $(VSRC_TMP_DIR:/=)/ $(VSRC_DIR)

# SRC_DIRS = common playground generator
# FIND_FILTER = -type f -name '*.scala'
# MILL_SRCS = $(shell find $(addprefix $(WORK_DIR)/,$(SRC_DIRS)) $(FIND_FILTER))

test:
	$(MILL) -i $(PRJ).test

$(VSRC_TIMESTAMP): force
	$(call git_commit, "generate verilog")
	-rm -rf $(VSRC_TMP_DIR)
	-mkdir -p $(VSRC_TMP_DIR)
	-mkdir -p $(VSRC_DIR)
	$(MILL) -i $(PRJ).runMain $(PACKAGE_NAME).Elaborate --target-dir $(VSRC_TMP_DIR)
	@if [ -n "$$($(RSYNC_CMD))" ]; then \
		echo "Verilog changed, updating timestamp..."; \
		touch $(VSRC_TIMESTAMP); \
	else \
		echo "Verilog unchanged."; \
	fi
	-$(MAKE) lint

verilog: $(VSRC_TIMESTAMP)

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

.PHONY: force test verilog help reformat checkformat bsp idea
