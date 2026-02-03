# NXDC_FILES = constr/$(TOPNAME).nxdc
# # constraint file
# SRC_AUTO_BIND = $(abspath $(BUILD_DIR)/auto_bind.cpp)
# $(SRC_AUTO_BIND): $(NXDC_FILES)
# 	python3 $(NVBOARD_HOME)/scripts/auto_pin_bind.py $^ $@
#
# test_path:
# 	@echo "[$(NXDC_FILES)]"
# 	@echo "[$(TOPNAME)]"
#
# # project source
# if ($(NVBOARD))
# 	CSRCS += $(SRC_AUTO_BIND)
# endif

#
# # rules for NVBoard
# include $(NVBOARD_HOME)/scripts/nvboard.mk
# ifeq ($(NVBOARD), off)
# 	NVBOARD_ARCHIVE := 
# 	CXXFLAGS += -DNO_NVBOARD
# endif
