#
# NEMU is licensed under Mulan PSL v2.
# You can use this software according to the terms and conditions of the Mulan PSL v2.
# You may obtain a copy of Mulan PSL v2 at:
#          http://license.coscl.org.cn/MulanPSL2
#
# THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
# EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
# MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
#
# See the Mulan PSL v2 for more details.
#**************************************************************************************/

-include $(NEMU_HOME)/../Makefile

include $(NEMU_HOME)/tools/difftest.mk
include $(NEMU_HOME)/tools/lib.mk
ARCHIVES += $(STATIC_LIBS)
$(info NEMUU archives $(ARCHIVES))

include $(NEMU_HOME)/scripts/build.mk

compile_git:
	$(call git_commit, "compile NEMU")
$(BINARY):: compile_git

# Some convenient rules

override ARGS ?= --log=$(BUILD_DIR)/nemu-log.txt
override ARGS += $(ARGS_DIFF)
override ARGS += $(ADD_ARGS)

$(info NEMU BUILD_DIR $(BUILD_DIR))

# Command to execute NEMU
IMG ?=
# NEMU_EXEC := numactl -m 0 -C 0,2,4,6 -- $(BINARY) $(ARGS) $(IMG)
NEMU_EXEC := $(BINARY) $(ARGS) $(IMG)

run-env: $(BINARY) $(DIFF_REF_SO)

run: run-env
	-mkdir -p $(BUILD_DIR)/../profile/
	$(call git_commit, "run NEMU")
	$(NEMU_EXEC)
	-mv -f $(NEMU_HOME)/profile.vlt $(BUILD_DIR)/../profile/profile.vlt
	-mv -f $(NEMU_HOME)/profile_exec.dat $(BUILD_DIR)/profile_exec.dat

gdb: run-env
	-mkdir -p $(BUILD_DIR)/../profile/
	$(call git_commit, "gdb NEMU")
	gdb -s $(BINARY) --args $(NEMU_EXEC)
	-mv -f $(NEMU_HOME)/profile.vlt $(BUILD_DIR)/../profile/profile.vlt
	-mv -f $(NEMU_HOME)/profile_exec.dat $(BUILD_DIR)/profile_exec.dat

clean-tools = $(dir $(shell find ./tools -maxdepth 2 -mindepth 2 -name "Makefile"))
$(clean-tools):
	-@$(MAKE) -s -C $@ clean
clean-tools: $(clean-tools)
clean-all: clean distclean clean-tools

.PHONY: run gdb run-env clean-tools clean-all $(clean-tools)
