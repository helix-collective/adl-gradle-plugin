import {makeCat, texprCat} from "./generated/sub";
import {createJsonBinding} from "./generated/runtime/json";
import {RESOLVER} from "./generated/resolver";

const cat = makeCat({name: 'Dinah-Kah', age: 10});

test('ADL object creation', () => {
    expect(cat.name).toBe('Dinah-Kah');
    expect(cat.age).toBe(10);
});

test('ADL JSON serialization', () => {
    const catJsonBinding = createJsonBinding(RESOLVER, texprCat());
    const catJson = catJsonBinding.toJson(cat);
    const deserializedCat = catJsonBinding.fromJsonE(catJson);
    expect(deserializedCat.name).toBe('Dinah-Kah');
    expect(deserializedCat.age).toBe(10);
});
