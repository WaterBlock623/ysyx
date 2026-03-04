WORK_DIR = $(shell pwd)
BUILD_DIR ?= $(WORK_DIR)/build
$(info NPC BUILD_DIR $(BUILD_DIR))
$(shell mkdir -p $(BUILD_DIR))

VSRC_TIMESTAMP = $(BUILD_DIR)/.vsrc_timestamp

all: build_ar

# Menuconfig
include $(NEMU_HOME)/scripts/config.mk
-include $(WORK_DIR)/include/config/auto.conf
-include $(WORK_DIR)/include/config/auto.conf.cmd

remove_quote = $(patsubst "%",%,$(1))

ifeq ($(CONFIG_NPC_PACKAGE_NAME),)
$(error PACKAGE_NAME cannot be empty)
else
PACKAGE_NAME = $(call remove_quote,$(CONFIG_NPC_PACKAGE_NAME))
$(info PACKAGE_NAME is $(PACKAGE_NAME))
ELABORATE_SCALA = ./playground/src/$(PACKAGE_NAME)/Elaborate.scala
# EXT_TOP_CMD = grep "emitSystemVerilogFile" $(ELABORATE_SCALA) | \
# 			  			grep "new " | \
# 			  			sed -E 's/.*new +([^ (,]+).*/\1/' | \
# 			  			sed 's/.*\.//'
EXT_TOP_CMD = cat $(ELABORATE_SCALA) | \
							tr -d '\n' | \
			  			sed -E 's/.*new +([^ (,]+).*/\1/' | \
			  			sed 's/.*\.//'
# TOPNAME = $(strip $(shell $(EXT_TOP_CMD)))
TOPNAME = ysyxSoCFull
ifeq ($(TOPNAME),)
$(error TOPNAME cannot be empty)
else
$(info TOPNAME is $(TOPNAME))
endif # TOPNAME
endif # PACKAGE_NAME

# Verilator
include $(WORK_DIR)/scripts/verilator.mk

# Chisel
include $(WORK_DIR)/scripts/chisel.mk

# Util
include $(WORK_DIR)/../Makefile

run: all
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	$(MAKE) -C $(NEMU_HOME) run $(NEMU_MAKE_FLAGS)

gdb: all
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	$(MAKE) -C $(NEMU_HOME) gdb $(NEMU_MAKE_FLAGS)

runbatch: all
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	$(MAKE) -C $(NEMU_HOME) run $(NEMU_MAKE_FLAGS)

clean:
	-rm -rf $(BUILD_DIR)

.PHONY: force all run gdb runbatch clean
