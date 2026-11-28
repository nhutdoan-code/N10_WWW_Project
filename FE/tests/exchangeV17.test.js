import test from 'node:test';
import assert from 'node:assert/strict';
import { exchangeQuote } from '../src/utils/afterSalesForm.js';
const order={tamTinh:16790000,giamGia:800000};
const product={maPhienBan:'OLD',donGia:16790000,soLuong:1};
const quote=(reason,id,price,stock=1)=>exchangeQuote(order,product,1,reason,{id,price,stock});
test('đổi 1–1 chỉ đúng phiên bản, không bù giá thị trường',()=>{
  assert.deepEqual(quote('Máy lỗi','OLD',20000000),{net:15990000,difference:0});
  assert.equal(quote('Giao sai hàng','NEW',20000000),null);
  assert.equal(quote('Máy lỗi','OLD',20000000,0),null);
});
test('đổi ý / không đúng mô tả không chọn giá dưới giá thực mua',()=>{
  assert.equal(quote('Đổi ý','NEW',15989999),null);
  assert.deepEqual(quote('Không đúng mô tả','NEW',15990000),{net:15990000,difference:0});
  assert.deepEqual(quote('Đổi ý','NEW',17990000),{net:15990000,difference:2000000});
  assert.equal(quote('Khác','NEW',17990000),null);
  assert.equal(quote('Đổi ý','NEW',17990000,0),null);
});
