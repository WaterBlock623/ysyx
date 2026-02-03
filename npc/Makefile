WORK_DIR = $(shell pwd)
BUILD_DIR = $(WORK_DIR)/build

$(shell mkdir -p $(BUILD_DIR))

PACKAGE_NAME = sirius
ELABORATE_SCALA = ./playground/src/$(PACKAGE_NAME)/Elaborate.scala
EXT_TOP_CMD = grep "emitSystemVerilogFile" $(ELABORATE_SCALA) | \
			  grep "new " | \
			  sed -E 's/.*new +([^ (,]+).*/\1/' | \
			  sed 's/.*\.//'
TOPNAME := $(strip $(shell $(EXT_TOP_CMD)))

ifeq ($(TOPNAME),)
$(error TOPNAME cannot be empty)
else
$(info TOPNAME is $(TOPNAME))
endif

# Menuconfig
include $(NEMU_HOME)/scripts/config.mk
-include $(WORK_DIR)/include/config/auto.conf
-include $(WORK_DIR)/include/config/auto.conf.cmd
remove_quote = $(patsubst "%",%,$(1))

# Verilator
include $(WORK_DIR)/scripts/verilator.mk

# Chisel
include $(WORK_DIR)/scripts/chisel.mk

# Util
include $(WORK_DIR)/../Makefile

clean:
	-rm -rf $(BUILD_DIR)

.PHONY: clean
