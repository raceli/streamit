#ifndef __STREAMIT_INTERNAL_H__
#define __STREAMIT_INTERNAL_H__

#include "streamit.h"

tape *create_tape_internal(int data_size, int tape_length);

void dispatch_messages(void);

#endif // __STREAMIT_INTERNAL_H__
