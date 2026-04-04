SRC_DIR = src
BUILD_DIR = build
$(shell mkdir -p $(BUILD_DIR))
BINARY = $(BUILD_DIR)/cachesim

SRCS = $(shell find $(SRC_DIR) -name '*.c')
OBJS = $(SRCS:$(SRC_DIR)/%.c=$(BUILD_DIR)/%.o)

CC = gcc
CFLAGS = -O3 -ggdb3 -lm -std=gnu11 -fsanitize=undefined -fsanitize=leak -fsanitize=address

all: $(BINARY)

$(BUILD_DIR)/%.o: $(SRC_DIR)/%.c
	$(CC) -c -o $@ $^ $(CFLAGS)

$(BINARY): $(OBJS)
	$(CC) -o $@ $^ $(CFLAGS)

clean:
	rm -rf $(BUILD_DIR)
