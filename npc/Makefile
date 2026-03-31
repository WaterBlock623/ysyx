remove_quote = $(patsubst "%",%,$(1))

ifneq ($(MAKECMDGOALS),clean)
ifeq ($(ARCH),)
$(error Need ARCH)
endif
ifneq ($(findstring ysyxsoc,$(ARCH)),) # ysyxsoc
CFG_DIR = $(NPC_HOME)/config/ysyxsoc
else # normal npc
CFG_DIR = $(NPC_HOME)/config/npc
endif
endif

BUILD_DIR ?= $(NPC_HOME)/build
$(info NPC BUILD_DIR $(BUILD_DIR))
$(shell mkdir -p $(BUILD_DIR))

VSRC_TIMESTAMP = $(BUILD_DIR)/.vsrc_timestamp

all: build_ar
	$(MAKE) -C $(NEMU_HOME) $(NEMU_MAKE_FLAGS)

# Menuconfig
# include $(NEMU_HOME)/scripts/config.mk
include $(CFG_DIR)/include/config/auto.conf
include $(CFG_DIR)/include/config/auto.conf.cmd

ifneq ($(MAKECMDGOALS),clean)
ifneq ($(MAKECMDGOALS),menuconfig)
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
# EXT_TOP_CMD = cat $(ELABORATE_SCALA) | \
# 							tr -d '\n' | \
# 			  			sed -E 's/.*new +([^ (,]+).*/\1/' | \
# 			  			sed 's/.*\.//'
# TOPNAME = $(strip $(shell $(EXT_TOP_CMD)))
TOPNAME = $(call remove_quote,$(CONFIG_NPC_TOP_NAME))
ifeq ($(TOPNAME),)
$(error TOPNAME cannot be empty)
else
$(info TOPNAME is $(TOPNAME))
endif # TOPNAME
endif # PACKAGE_NAME
endif # menuconfig
endif # clean

# Verilator
include $(NPC_HOME)/scripts/verilator.mk

# Chisel
include $(NPC_HOME)/scripts/chisel.mk

# Util
include $(NPC_HOME)/../Makefile

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

menuconfig:
	$(MAKE) -C $(CFG_DIR) -f $(NEMU_HOME)/scripts/config.mk menuconfig

.PHONY: force all run gdb runbatch clean menuconfig
