IVERILOG_HEX = $(BUILD_DIR)/$(notdir $(basename $(IMG)))_iverilog.hex
IVERILOG_VSRCS += $(NPC_HOME)/iverilog/iverilog_top.sv
IVERILOG_VSRCS += $(NPC_HOME)/build/ysyx_26010008.sv

$(IVERILOG_HEX): $(IMG)
	mkdir -p $(dir $@)
	# objcopy -I binary -O verilog $(IMG) $@
	hexdump -v -e '1/4 "%08x" "\n"' $(IMG) > $@

sim-iverilog: $(IVERILOG_HEX)
	$(MAKE) -C $(NPC_HOME) verilog ARCH=iverilog
	iverilog -g2012 -s iverilog_top -o $(BUILD_DIR)/iverilog.vvp \
		-D IVERILOG_HEX_PATH="\"$(IVERILOG_HEX)\"" \
		$(IVERILOG_VSRCS)
	vvp $(BUILD_DIR)/iverilog.vvp

sim-iverilog-netlist: $(IVERILOG_HEX)
