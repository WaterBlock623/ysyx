IVERILOG_HEX = $(BUILD_DIR)/$(notdir $(basename $(IMG)))_iverilog.hex
IVERILOG_VSRCS = $(BUILD_DIR)/iverilog-vsrc/*.sv $(NPC_HOME)/iverilog/iverilog_top.sv

$(IVERILOG_HEX):
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
