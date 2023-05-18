#include <stdlib.h>
#define Export __attribute__((visibility("default")))

Export
void *_allocate(size_t size) {
  return malloc(size);
}

Export
void _deallocate(void *p) {
  free(p);
}

typedef struct {
  int _len;
  signed char *_data;
} _string;

typedef struct {
  _Bool _some;
  int _data;
} _option_int;

typedef struct {
  int _capacity;
  int _len;
  _string *_key;
  int *_value;
} _map_string_int;

//extern void *malloc(unsigned long long);

Export
_Bool _success;

Export
_string _msg;

Export
_string sender;

Export
_map_string_int ledger;

Export
_string owner;

_string _p_tmp1 = { 0, 0, };

signed char __p_tmp2[42] = { 69, 114, 114, 111, 114, 58, 32, 116, 104, 101,
  32, 105, 110, 105, 116, 32, 102, 117, 110, 99, 116, 105, 111, 110, 32, 97,
  108, 114, 101, 97, 100, 121, 32, 98, 101, 32, 99, 97, 108, 108, 101, 100,
  };

_string _p_tmp2 = { 42, __p_tmp2, };

signed char __p_tmp3[52] = { 69, 114, 114, 111, 114, 58, 32, 116, 104, 101,
  32, 109, 105, 110, 116, 32, 102, 117, 110, 99, 116, 105, 111, 110, 32, 109,
  117, 115, 116, 32, 98, 101, 32, 99, 97, 108, 108, 101, 100, 32, 98, 121,
  32, 116, 104, 101, 32, 111, 119, 110, 101, 114, };

_string _p_tmp3 = { 52, __p_tmp3, };

signed char __p_tmp4[19] = { 69, 114, 114, 111, 114, 58, 32, 116, 104, 101,
  32, 97, 99, 99, 111, 117, 110, 116, 32, };

_string _p_tmp4 = { 19, __p_tmp4, };

signed char __p_tmp5[16] = { 32, 104, 97, 115, 32, 110, 111, 32, 98, 97, 108,
  97, 110, 99, 101, 46, };

_string _p_tmp5 = { 16, __p_tmp5, };

signed char __p_tmp6[23] = { 32, 104, 97, 115, 32, 110, 111, 32, 101, 110,
  111, 117, 103, 104, 32, 98, 97, 108, 97, 110, 99, 101, 46, };

_string _p_tmp6 = { 23, __p_tmp6, };

signed char __out_of_bounds[19] = { 105, 110, 100, 101, 120, 32, 111, 117,
  116, 32, 111, 102, 32, 98, 111, 117, 110, 100, 115, };

_string _out_of_bounds = { 19, __out_of_bounds, };

signed char __div_by_zero[33] = { 97, 114, 105, 116, 104, 109, 101, 116, 105,
  99, 32, 101, 120, 99, 101, 112, 116, 105, 111, 110, 58, 32, 100, 105, 118,
  32, 98, 121, 32, 122, 101, 114, 111, };

_string _div_by_zero = { 33, __div_by_zero, };

signed char __mod_by_zero[33] = { 97, 114, 105, 116, 104, 109, 101, 116, 105,
  99, 32, 101, 120, 99, 101, 112, 116, 105, 111, 110, 58, 32, 109, 111, 100,
  32, 98, 121, 32, 122, 101, 114, 111, };

_string _mod_by_zero = { 33, __mod_by_zero, };

signed char __get_none[25] = { 99, 97, 110, 39, 116, 32, 103, 101, 116, 32,
  118, 97, 108, 117, 101, 32, 102, 114, 111, 109, 32, 110, 111, 110, 101, };

_string _get_none = { 25, __get_none, };

signed char __malloc_failed[13] = { 109, 97, 108, 108, 111, 99, 32, 102, 97,
  105, 108, 101, 100, };

_string _malloc_failed = { 13, __malloc_failed, };

void _assign_string(_string *_a1, _string *_out)
{
  int _i;
  register signed char *$88;
  (*_out)._len = (*_a1)._len;
  $88 = malloc(sizeof(signed char) * (*_out)._len);
  (*_out)._data = $88;
  if ((*_out)._data == 0) {
    _success = 0;
    _msg = _malloc_failed;
    return;
  }
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_out)._len)) {
      break;
    }
    (*_out)._data[_i] = (*_a1)._data[_i];
  }
}

void _equals_string(_string *_a1, _string *_a2, _Bool *_out)
{
  int _i;
  *_out = 0;
  if ((*_a1)._len != (*_a2)._len) {
    return;
  }
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_a1)._len)) {
      break;
    }
    if ((*_a1)._data[_i] != (*_a2)._data[_i]) {
      return;
    }
  }
  *_out = 1;
}

void _concat_string(_string *_a1, _string *_a2, _string *_out)
{
  int _i;
  register signed char *$88;
  (*_out)._len = (*_a1)._len + (*_a2)._len;
  $88 = malloc(sizeof(signed char) * (*_out)._len);
  (*_out)._data = $88;
  if ((*_out)._data == 0) {
    _success = 0;
    _msg = _malloc_failed;
    return;
  }
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_a1)._len)) {
      break;
    }
    (*_out)._data[_i] = (*_a1)._data[_i];
  }
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_a2)._len)) {
      break;
    }
    (*_out)._data[(_i + (*_a1)._len)] = (*_a2)._data[_i];
  }
}

void _substring_string(_string *_a1, int _a2, int _a3, _string *_out)
{
  int _i;
  register signed char *$88;
  (*_out)._len = _a3 - _a2;
  $88 = malloc(sizeof(signed char) * (*_out)._len);
  (*_out)._data = $88;
  if ((*_out)._data == 0) {
    _success = 0;
    _msg = _malloc_failed;
    return;
  }
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_out)._len)) {
      break;
    }
    (*_out)._data[_i] = (*_a1)._data[(_i + _a2)];
  }
}

void _assign_map_string_int(_map_string_int *_a1, _map_string_int *_out)
{
  int _i;
  register _string *$88;
  register int *$89;
  (*_out)._capacity = (*_a1)._capacity;
  (*_out)._len = (*_a1)._len;
  $88 = malloc(sizeof(_string) * (*_out)._capacity);
  (*_out)._key = $88;
  if ((*_out)._key == 0) {
    _success = 0;
    _msg = _malloc_failed;
    return;
  }
  $89 = malloc(sizeof(int) * (*_out)._capacity);
  (*_out)._value = $89;
  if ((*_out)._value == 0) {
    _success = 0;
    _msg = _malloc_failed;
    return;
  }
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_out)._len)) {
      break;
    }
    _assign_string((*_a1)._key + _i, (*_out)._key + _i);
    if (_success == 0) {
      return;
    }
    (*_out)._value[_i] = (*_a1)._value[_i];
  }
}

void _get_map_string_int(_map_string_int *_a1, _string *_a2, _option_int *_out)
{
  int _i;
  _Bool _v1;
  (*_out)._some = 0;
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_a1)._len)) {
      break;
    }
    _equals_string((*_a1)._key + _i, _a2, &_v1);
    if (_success == 0) {
      return;
    }
    if (_v1 == 1) {
      (*_out)._some = 1;
      (*_out)._data = (*_a1)._value[_i];
    }
  }
}

void _set_map_string_int(_string *_a1, int _a2, _map_string_int *_out)
{
  int _i;
  _Bool _v1;
  _string *_t1;
  int *_t2;
  register _string *$88;
  register int *$89;
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_out)._len)) {
      break;
    }
    _equals_string((*_out)._key + _i, _a1, &_v1);
    if (_success == 0) {
      return;
    }
    if (_v1 == 1) {
      (*_out)._value[_i] = _a2;
      return;
    }
  }
  if ((*_out)._len == (*_out)._capacity) {
    (*_out)._capacity = (*_out)._capacity * 2 + 4;
    $88 = malloc(sizeof(_string) * (*_out)._capacity);
    _t1 = $88;
    if (_t1 == 0) {
      _success = 0;
      _msg = _malloc_failed;
      return;
    }
    $89 = malloc(sizeof(int) * (*_out)._capacity);
    _t2 = $89;
    if (_t2 == 0) {
      _success = 0;
      _msg = _malloc_failed;
      return;
    }
    _i = 0;
    for (; 1; _i = _i + 1) {
      if (! (_i < (*_out)._len)) {
        break;
      }
      _assign_string((*_out)._key + _i, _t1 + _i);
      if (_success == 0) {
        return;
      }
      _t2[_i] = (*_out)._value[_i];
    }
    (*_out)._key = _t1;
    (*_out)._value = _t2;
  }
  _assign_string(_a1, (*_out)._key + (*_out)._len);
  if (_success == 0) {
    return;
  }
  (*_out)._value[(*_out)._len] = _a2;
  (*_out)._len = (*_out)._len + 1;
}

Export
void _init(_Bool __success, _string *__msg, _string *_sender, _map_string_int *_ledger, _string *_owner)
{
  _success = __success;
  _msg = *__msg;
  sender = *_sender;
  ledger = *_ledger;
  owner = *_owner;
}

Export
void _terminate(_Bool *__success, _string *__msg, _string *_sender, _map_string_int *_ledger, _string *_owner)
{
  *__success = _success;
  *__msg = _msg;
  *_sender = sender;
  *_ledger = ledger;
  *_owner = owner;
}

Export
void init(int balance)
{
  _Bool _f_tmp1;
  _equals_string(&owner, &_p_tmp1, &_f_tmp1);
  if (_success == 0) {
    return;
  }
  if (!_f_tmp1) {
    _success = 0;
    _msg = _p_tmp2;
    return;
  }
  _set_map_string_int(&sender, balance, &ledger);
  if (_success == 0) {
    return;
  }
  /*skip*/;
  _assign_string(&sender, &owner);
  if (_success == 0) {
    return;
  }
}

Export
void mint(int amount)
{
  int newBalance;
  _option_int balanceOption;
  _Bool _f_tmp1;
  int _f_tmp2;
  newBalance = 0;
  balanceOption._some = 0;
  _equals_string(&owner, &sender, &_f_tmp1);
  if (_success == 0) {
    return;
  }
  if (!_f_tmp1) {
    _success = 0;
    _msg = _p_tmp3;
    return;
  }
  _get_map_string_int(&ledger, &sender, &balanceOption);
  if (_success == 0) {
    return;
  }
  if (balanceOption._some == 0) {
    _success = 0;
    _msg = _get_none;
    return;
  }
  _f_tmp2 = balanceOption._data;
  newBalance = _f_tmp2 + amount;
  _set_map_string_int(&sender, newBalance, &ledger);
  if (_success == 0) {
    return;
  }
  /*skip*/;
}

Export
void transfer(_string *_recipient, int amount)
{
  _string recipient;
  int balanceOfSpender;
  int newBalanceOfSpender;
  int balanceOfRecipient;
  int newBalanceOfRecipient;
  _option_int balanceOfRecipientOption;
  _option_int balanceOfSpenderOption;
  _string _f_tmp1;
  _string _f_tmp2;
  _string _f_tmp3;
  _string _f_tmp4;
  _assign_string(_recipient, &recipient);
  if (_success == 0) {
    return;
  }
  balanceOfSpender = 0;
  newBalanceOfSpender = 0;
  balanceOfRecipient = 0;
  newBalanceOfRecipient = 0;
  balanceOfRecipientOption._some = 0;
  _get_map_string_int(&ledger, &sender, &balanceOfSpenderOption);
  if (_success == 0) {
    return;
  }
  /*skip*/;
  _concat_string(&_p_tmp4, &sender, &_f_tmp1);
  if (_success == 0) {
    return;
  }
  _concat_string(&_f_tmp1, &_p_tmp5, &_f_tmp2);
  if (_success == 0) {
    return;
  }
  if (!!!balanceOfSpenderOption._some) {
    _success = 0;
    _msg = _f_tmp2;
    return;
  }
  if (balanceOfSpenderOption._some == 0) {
    _success = 0;
    _msg = _get_none;
    return;
  }
  balanceOfSpender = balanceOfSpenderOption._data;
  /*skip*/;
  _concat_string(&_p_tmp4, &sender, &_f_tmp3);
  if (_success == 0) {
    return;
  }
  _concat_string(&_f_tmp3, &_p_tmp6, &_f_tmp4);
  if (_success == 0) {
    return;
  }
  if (!(balanceOfSpender >= amount)) {
    _success = 0;
    _msg = _f_tmp4;
    return;
  }
  _get_map_string_int(&ledger, &recipient, &balanceOfRecipientOption);
  if (_success == 0) {
    return;
  }
  /*skip*/;
  if (!!balanceOfRecipientOption._some) {
    if (balanceOfRecipientOption._some == 0) {
      _success = 0;
      _msg = _get_none;
      return;
    }
    balanceOfRecipient = balanceOfRecipientOption._data;
  }
  newBalanceOfSpender = balanceOfSpender - amount;
  newBalanceOfRecipient = balanceOfRecipient + amount;
  _set_map_string_int(&sender, newBalanceOfSpender, &ledger);
  if (_success == 0) {
    return;
  }
  /*skip*/;
  _set_map_string_int(&recipient, newBalanceOfRecipient, &ledger);
  if (_success == 0) {
    return;
  }
  /*skip*/;
}


