MILL = $(NPC_HOME)/mill
RSYNC = rsync
PRJ = playground

VSRC_TMP_DIR = $(BUILD_DIR)/$(ARCH)-vsrc_tmp
VSRC_DIR = $(BUILD_DIR)/$(ARCH)-vsrc

RSYNC_CMD = $(RSYNC) -rlpgoD --checksum --delete --itemize-changes \
						--omit-dir-times $(VSRC_TMP_DIR:/=)/ $(VSRC_DIR)

SRC_DIRS = common playground generator
SEARCH_DIRS = $(addprefix $(NPC_HOME)/,$(SRC_DIRS))
FIND_FILTER = -type f -name '*.scala'
MILL_SRCS = $(shell find $(SEARCH_DIRS) $(FIND_FILTER))
VSRC_TIMESTAMP = $(BUILD_DIR)/.$(ARCH)-vsrc_timestamp

ifneq ($(MAKECMDGOALS),clean)
ifeq ($(ARCH),)
$(error Need ARCH)
endif
ifneq ($(findstring ysyxsoc,$(ARCH)),) # ysyxsoc
SCALA_FLAGS = --ysyxsoc true --pc-init 0x30000000 --debug true --perf true
else ifneq ($(findstring syn,$(ARCH)),) # syn
SCALA_FLAGS = --ysyxsoc true --pc-init 0x30000000 --debug false --perf false
else # normal npc
SCALA_FLAGS = --ysyxsoc false --pc-init 0x80000000 --debug true --perf false
endif
endif

test:
	$(MILL) $(PRJ).test.testOnly $(PACKAGE_NAME).*

$(VSRC_TIMESTAMP): $(MILL_SRCS) $(SEARCH_DIRS)
	# Generate verilogs
	$(call git_commit, "generate verilog")
	-rm -rf $(VSRC_TMP_DIR)
	-mkdir -p $(VSRC_TMP_DIR)
	-mkdir -p $(VSRC_DIR)
	$(MILL) -i $(PRJ).runMain $(PACKAGE_NAME).Elaborate \
		--target-dir $(VSRC_TMP_DIR) $(SCALA_FLAGS)
	$(RSYNC_CMD)
	-$(MAKE) lint
	touch $@

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



.PHONY: test verilog help reformat checkformat bsp idea
