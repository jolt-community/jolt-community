# JOLT Community Edition

## Introduction

JOLT Community Edition is a community-maintained edition of JOLT, a JSON to JSON transformation library written in Java. For the original version, please visit the [bazaarvoice/jolt](https://github.com/bazaarvoice/jolt) repository.

## Getting Started

**TODO**

## Learning JOLT

### JOLT Demo

An interactive JOLT (v0.1.1) demo site is available at https://jolt-demo.appspot.com/#inception. Version 0.1.1 is a very early version of JOLT, so not all features may be supported.

### LLM Support

Large Language Models struggle to reliably generate non-trivial (and sometimes even trivial) JOLT specs. LLMs such as OpenAI's ChatGPT-4o and Anthropic's Claude frequently generate invalid JOLT syntax, hallucinate nonexistent functions, and even imagine entire capabilities that do not exist in JOLT. They also tend to "forget" in conversation that certain suggestions are invalid, especially while using search capabilities. Like many niche domain-specific languages (DSLs), JOLT does not have a wide dataset of examples to train on. Furthermore, Official JOLT documentation has been fairly sparse. If LLM support is a must, you may have better luck with a traditional scripting language or a more popular JSON transformation DSL.

### Terminology

This documentation follows the terminology set out by RFC 8259, with one notable exception. To reduce confusion, the term "key" will be used in place of the more traditional terms "name" or "member name". When used, the term "name" exclusively refers to the actual value of the string which is being used as a key.

| JSON Term | Definition | Example |
|-----------|------------|------------|
| String    | A sequence of zero or more Unicode characters in double quotes, supporting backslash escapes (`\"`, `\\`, `\uXXXX`).| `"hello world"` |
| Number    | A base-10 signed decimal literal: optional minus; integer part (no leading zeros unless zero); optional fraction; optional exponent (`E`/`e` plus digits); `NaN` and `Infinity` are disallowed. (RFC 8259 §6) | `0.0001`, `1234` |
| Boolean   | Exactly one of the literals: `true` or `false`. | `true`,`false` |
| Null      | The literal `null`, representing an explicit empty value. | `null` |
| Value     | Any valid JSON type: string, number, boolean, null, array, or object. | |
| Array     | An ordered, comma-separated sequence of zero or more values, enclosed in square brackets `[...]`. | `[0, "abc", {}]` |
| Element   | A single value within an array. | `"abc"` in `[0, "abc", {}]` |
| Key       | A string serving as the identifier for a value.  | `"id"`, `"Label"`, `"settings"` |
| Attribute | A key, followed by `:`, followed by a value Sometimes called a key/value pair. | `"key":"value"` |
| Object    | An unordered set of zero or more attributes, enclosed in `{...}`. Keys should be unique. | `{"a":"b"}` |

In addition to these "traditional" terms, we also define several "applied" terms, which may appear infrequently.

| Extended JSON Term | Definition |
|-----------|------------|
| Index    | A number, starting with 0, representing the position (left-to-right) of an element within an array. When an array is cast to an object, the index is used as the key for the given value of the element (after being cast to string). |
| Path    | An ordered sequence of keys and/or indices which can be traversed in order to arrive at a desired value. |
| Root    | The outermost value, i.e. the entire JSON object itself. Typically an array or object. Often denoted as `$`, especially in paths.  |
| Dot Notation | A representation format for a path where keys are delimited by the character `.` in-between names. E.g. `$.settings.users.display_name`. Use is discouraged if any of the names contains the character `.`. |
| Bracket Notation | A representation format for a path where keys and indices are wrapped in square brackets. E.g. `$[0]["settings"]["users"]["display_name"]` |


## Operations

In JOLT, an operation is a certain type of data transformation. Each operation has it's own domain-specific language. By default, JOLT comes with several core operations:

1. shift: move data from one path to another
2. default: provide attributes if they do not already exist
3. remove: remove attributes from an object, or elements from an array
4. cardinality: ensure that values are either arrays or not arrays
5. sort: order the keys of a JSON object deterministically.

### Specification


### The `shift` Operation

`shift` is a kind of JOLT transform that specifies where "data" from the input JSON should be placed in the output JSON. At a base level, a single `shift` operation maps data from an input path to an output path.

The spec syntax tends to follow this format:

```json
{
  "original_key": "new_key"
}
```

On the left-hand side (LHS), we list where the data currently is, and on the right-hand side (RHS), we list the key that the data should be moved to.

There are several important facts to know about the `shift` operation:

+ More advanced syntax for `shift` often differs between the LHS and RHS.
+ The `shift` operation provides a wide number of wildcard symbols which make it flexible and powerful.
+ Any data not shifted in the `shift` spec will disappear. To keep unshifted data as-is, we must shift all "unmentioned" data to it's current location. This can be done easily with the use of wildcards.
+ If a key on the LHS does not exist within a JSON input, that key is ignored, and no error is raised.

#### Shifting Nested JSON: LHS vs RHS

In `shift`, a nested input path is specified via a JSON tree structure, and the output path is specified via a flattened "dot notation" path.

For example, given the following JSON:

```json
{
  "keep": {
    "old":"shift me to keep.new"
  }
}
```
to make the following JSON

```json
{
  "keep": {
    "new":"shift me to keep.new"
  }
}
```

The CORRECT syntax for shifting `keep.old` to `keep.new` would be:

```json title="Spec A"
{
  "keep": {
    "old":"keep.new"
  }
}
```

This would be INCORRECT syntax for shifting `keep.old` to `keep.new`:

```json title="Spec B"
{
  "keep.old": "keep.new"
}
```

While counter-intuitive, the nested key syntax on the LHS disambiguates nested and dot-flattened input keys. For example, in the below input, the LHS key `"keep.old"` might match on multiple locations, causing confusion and ambiguity.

```json
{
  "keep": {
    "old":"shift me to keep.new"
  },
  "keep.old": "do not shift this value to keep.new"
}
```

Currently, the "INCORRECT" spec would actually output the following:

```json
{
  "keep": {
    "new":"do not shift this value to keep.new"
  }
}
```

#### `shift` Wildcards

As shown above, `shift` specs can be entirely made up of literal string values, but its real power comes from symbolic wildcards which provide elegant access to nested keys, indexes, existing values, and more. Wildcard symbols are used _within the string_ on the LHS or RHS. Some wildcard symbols can be used on both the LHS and RHS, and some are only valid on one side only.

| Symbol | Wildcard Name | LHS | RHS |
| - | - | - | - |
| `*` | Name | Non-greedy wildcard matching of key names | Not Valid on RHS |
| `\|` | ANY/OR | Used as delimiter in the LHS string to indicate matches on one of several arbitrary keys | Not Valid on RHS |
| `&` | Path as Key | Use a key in a nearby location | Copies elements of the current path in the output path |
| `$` | Key as Value | Use a key as the value in the output | Not Valid on RHS |
| `#` | Synthetic (Value/Index) | Synthetic value: use whatever follows afterwards as a literal value | Synthetic Index: Reference the index value of a match on a different array |

##### Essential Wildcard Expressions
Some wildcard expressions are so important, they are worth mentioning here, before we go into depth about each symbol.

###### The `"*":"&"` Idiom: Keep All Data

Recall one of the most important facts about `shift`:

> Any data not shifted in the `shift` spec will disappear. To keep unshifted data as-is, we must shift all "unmentioned" data to it's current location.

This spec matches all key names in the root level of the json, and maps them to their current key. 

```json
{
  "*":"&"
}
```

This is effectively a no-op, but shifting the key back to itself prevents it from being removed.

####### The `"*":"&"` Idiom Is Often Used Multiple Times In A Spec

Explicitly shifting a key excludes it from the `*` wildcard.  Furthermore, if one of a sub-object's attributes is explicitly shifted within the spec, any unshifted attributes within that sub-object will not be kept. Therefore, you may see the `"*":"&"` idiom more than once within a spec, particularly when sub-objects are being manipulated and unmentioned sub-attributes need to remain as-is. However, otherwise untouched nested objects kept with a `"*":"&"` will remain intact.

For example, take the following input JSON, with three sub-objects.

```json
{
  "untouched":{"a":true, "b":{"c":true}},
  "root_shift":{"a":true, "b":{"c":true}},
  "subobject_shift":{"a":true, "b":{"c":true}}
}
```
In a spec which uses `"*":"&"` at the root level, such as:

```json
{
  "*":"&",
  "root_shift":"SHIFTED_root_shift",
  "subobject_shift":{"a":"subobject_shift.SHIFTED_a"}
}
```

You will see that:
1. The `"untouched"` sub-object kept via the `"*":"&"` idiom keeps all sub-attributes.
2. The explicitly shifted sub-object `"root_shift"` mapped to a new key keeps it's sub-attributes.
3. The sub-object `"subobject_shift"` is now missing the attribute `"b":{"c":true}`, however, because it did have a different sub-attribute shifted, and `"b":{"c":true}` was unshifted. `"b":{"c":true}` was not kept in place by the `"*":"&"` idiom because `"subobject_shift"` is explicitly shifted, and explicitly shifting a key excludes it from the `*` wildcard.

```json
{
  "untouched":{"a":true, "b":{"c":true}},
  "SHIFTED_root_shift":{"a":true, "b":{"c":true}},
  "subobject_shift":{"SHIFTED_a":true}
}
```

To keep `"b":{"c":true}` within `"subobject_shift"`, we must use a second `"*":"&"` idiom, within `"subobject_shift"`:

```json
{
  "*":"&",
  "root_shift":"SHIFTED_root_shift",
  "subobject_shift":{"*":"subobject_shift.&", "a":"subobject_shift.SHIFTED_a"}
}
```

It is worth noting, however, that the `&` wildcard allows us to write this spec more concisely:

```json
{
  "*":"&",
  "root_shift":"SHIFTED_&",
  "subobject_shift":{"*":"&1.&", "a":"&1.SHIFTED_&"}
}
```  

##### Wildcard Parameters

Using wildcards, you can leverage the fact that you know, not just the data and its immediate key, but the whole input path to that data.

Expanding the example above, say we have the following expanded Input JSON:

```json
{
  "rating": {
      "primary": {
          "value": 3,   // want this value to goto output path "Rating"
          "max": 5      // want this value to goto output path "RatingRange"
      },
      "quality": {      // want output path "SecondaryRatings.quality.Id" = "quality", aka we want the value of the key to be used
          "value": 3,   // want this value to goto output path "SecondaryRatings.quality.Value"
          "max": 5      // want this value to goto output path "SecondaryRatings.quality.Range"
      },
      "sharpness": {   // want output path "SecondaryRatings.sharpness.Id" = "sharpness"
          "value": 7,  // want this value to goto output path "SecondaryRatings.sharpness.Value"
          "max": 10    // want this value to goto output path "SecondaryRatings.sharpness.Range"
      }
  }
}
```

The Spec would be:

```json
{
  "rating": {
    "primary": {
        "value": "Rating",                       // output -> "Rating" : 3
        "max": "RatingRange"                     // output -> "RatingRange" : 5
    },
    "*": {                                       // match input data like "rating.[anything-other-than-primary]"
        "value": "SecondaryRatings.&1.Value",    // the data at "rating.*.value" goes to "SecondaryRatings.*.Value"
                                                 // the "&1" means use the value one level up the tree ( "quality" or "sharpness" )
                                                 // output -> "SecondaryRatings.quality.Value" : 3 AND
                                                 //           "SecondaryRatings.sharpness.Value" : 7

        "max": "SecondaryRatings.&1.Range",      // the data at "rating.*.max" goes to "SecondaryRatings.*.Range"
                                                 // the "&1" means use the value one level up the tree ( "quality" or "sharpness" )
                                                 // output -> "SecondaryRatings.quality.Range" : 5 AND
                                                 //           "SecondaryRatings.sharpness.Range" : 10

        "$": "SecondaryRatings.&1.Id"            // Special operator $ means, use the value of the input key itself as the data
                                                 // output -> "SecondaryRatings.quality.Id" : "quality"
                                                 // output -> "SecondaryRatings.sharpness.Id" : "sharpness"
    }
  }
}
```

Yielding the following output:

```json
{
  "Rating": 3,
  "RatingRange": 5,
  "SecondaryRatings": {
     "quality": {
        "Range": 5,
        "Value": 3,
        "Id": "quality"     // the special $ operator allows us to use input key the text value of "quality", as the "Id" of the output
     },
     "sharpness": {
        "Range": 10,
        "Value": 7,
        "Id": "sharpness"   // the special $ operator allows us to use input key the text value of "sharpness", as the "Id" of the output
     }
  }
}
```

#### `shift` Wildcards



##### `*` Wildcard
Valid only on the LHS ( input JSON keys ) side of a `shift` Spec
The `*` wildcard can be used by itself or to match part of a key.

`*` wildcard by itself:
As illustrated in the example above, the `*` wildcard by itself is useful for "templating" JSON maps,
where each key / value has the same "format".

```json
// example input
{
  "rating" : {
    "quality": {
      "value": 3,
      "max": 5
    },
    "sharpness" : {
      "value" : 7,
      "max" : 10
    }
}
```

In this example, "rating.quality" and "rating.sharpness" both have the same structure/format, and thus we can use the `*`
to allow us to write more compact rules and avoid having to explicitly write very similar rules for both "quality" and "sharpness".

`*` wildcard as part of a key:
This is useful for working with input JSON with keys that are "prefixed".
Ex: if you had an input document like

```json
{
  "tag-Pro": "Awesome",
  "tag-Con": "Bogus"
}
```

A 'tag-*' would match both keys, and make the whole key and "matched" part of the key available.
Ex, input key of "tag-Pro" with LHS spec "tag-*", would "tag-Pro" and "Pro" available to reference.
Note the `*` wildcard is as non-greedy as possible, hence you can use more than one `*` in a key.
For example, "tag-*-*" would match "tag-Foo-Bar", making "tag-Foo-Bar", "Foo", and "Bar" all available to reference.

##### `&` Wildcard
Valid on the LHS (left hand side - input JSON keys) and RHS (output data path)

Means, dereference against a "path" to get a value and use that value as if were a literal key.
The canonical form of the wildcard is "&(0,0)".
The first parameter is where in the input path to look for a value, and the second parameter is which part of the key to use (used with key).
There are syntactic sugar versions of the wildcard, all of the following mean the same thing.
  Sugar : `&` = `&0` = `&(0)` = `&(0,0)`
The syntactic sugar versions are nice, as there are a set of data transforms that do not need to use the canonical form, eg if your input data does not have any "prefixed" keys.

##### `&` Path lookup
As `shift` processes data and walks down the spec, it maintains a data structure describing the path it has walked.
The `&` wildcard can access data from that path in a 0 major, upward oriented way.

Example:

```json
{
    "foo" : {
        "bar": {
            "baz":  // &0 = baz, &1 = bar, &2 = foo
        }
    }
}
```

##### `&` Subkey lookup
`&` subkey lookup allows us to reference the values captured by the `*` wildcard.

Example, "tag-*-*" would match "tag-Foo-Bar", making
  &(0,0) = "tag-Foo-Bar"
  &(0,1) = "Foo"
  &(0,2) = "Bar"

`$` Wildcard
  Valid only on the LHS of the spec.
  The existence of this wildcard is a reflection of the fact that the "data" of the input JSON, can be both in the "values"
   and the "keys" of the input JSON

  The base case operation of `shift` is to copy input JSON "values", thus we need a way to specify that we want to copy the input JSON "key" instead.

  Thus `$` specifies that we want to use an input key, or input key derived value, as the data to be placed in the output JSON.
  `$` has the same syntax as the `&` wildcard, and can be read as, dereference to get a value, and then use that value as the data to be output.

  There are two cases where this is useful
    1) when a "key" in the input JSON needs to be a "id" value in the output JSON, see the `"$": "SecondaryRatings.&1.Id"` example above.
    2) you want to make a list of all the input keys.

  Example of "a list of the input keys":

  ```json
  // input
  {
    "rating": {
      "primary": {
        "value": 3,
        "max": 5
      },
      "quality": {
        "value": 3,
        "max": 7
      }
    }
  }

  // desired output
  {
    "ratings" : [ "primary", "quality" ]    // Aside: this is an example of implicit JSON array creation in the output which is detailed further down.
                                            // For now just observe that the input keys "primary" and "quality" have both made it to the output.
  }

  // spec
  {
    "rating": {
      "*": {               // match all keys below "rating"
        "$": "ratings"     // output each of the "keys" to "ratings" in the output
      }
    }
  }
  ```

##### `#` Wildcard
Valid both on the LHS and RHS, but has different behavior / format on either side.
The way to think of it, is that it allows you to specify a "synthentic" value, aka a value not found in the input data.

On the RHS of the spec, # is only valid in the the context of an array, like "[#2]".
What "[#2]" means is, go up the three levels and ask that node how many matches it has had, and then use that as an index
  in the arrays.
This means that, while `shift` is doing its parallel tree walk of the input data and the spec, it tracks how many matches it
  has processed at each level of the spec tree.

This useful if you want to take a JSON map and turn it into a JSON array, and you do not care about the order of the array.

On the LHS of the spec, # allows you to specify a hard coded String to be place as a value in the output.

The initial use-case for this feature was to be able to process a Boolean input value, and if the value is
  boolean true write out the string "enabled".  Note, this was possible before, but it required two `shift` steps.

```json
    "hidden" : {
        "true" : {                             // if the value of "hidden" is true
            "#disabled" : "clients.clientId"   // write the word "disabled" to the path "clients.clientId"
        }
    }
```


##### `|` Wildcard
  Valid only on the LHS of the spec.
  This 'or' wildcard allows you to match multiple input keys.   Useful if you don't always know exactly what your input data will be.
  Example Spec :
  ```json
  {
    "rating|Rating" : "rating-primary"   // match "rating" or "Rating" copy the data to "rating-primary"
  }
  ```
  This is really just syntactic sugar, as the implementation really just treats the key "rating|Rating" as two keys when processing.


##### `@` Wildcard
  Valid on both sides of the spec.

  The basic `@` on the LHS.

  This wildcard is necessary if you want to put both the input value and the input key somewhere in the output JSON.

 Example `@` wildcard usage :

 ```json
 // Say we have a spec that just operates on the value of the input key "rating"
 {
    "foo" : "place.to.put.value",  // leveraging the implicit operation of `shift` which is to operate on input JSON values
 }

 // if we want to do something with the "key" as well as the value
 {
    "foo" : {
      "$" : "place.to.put.key",
      "@" : "place.to.put.value"    // `@` explicitly tell `shift` to operate on the input JSON value of the parent key "foo"
    }
 }
 ```

 Thus the `@` wildcard is the mean "copy the value of the data at this level in the tree, to the output".

 Advanced `@` sign wildcard.
 The format is lools like "@(3,title)", where
   "3" means go up the tree 3 levels and then lookup the key
   "title" and use the value at that key.

 See the filter*.json and transpose*.json Unit Test fixtures.


#### JSON Arrays:

Reading from (input) and writing to (output) JSON Arrays is fully supported.

1) Handling Arrays in the input JSON
`shift` treats JSON arrays in the input data as Maps with numeric keys.
Example :
```json
  // input
  {
    "Photos": [ "AAA.jpg", "BBB.jpg" ]
  }

  // spec
  {
    "Photos" :
    {
      "1" : "photo-&-url"      // Specify that we only want to operate on the 1-th index of the "Photos" input array
    }
  }

// output
{
    "photo-1-url": "BBB.jpg"
}
```


2) Handling Arrays in the output JSON
Traditional array brackets, [ ], are used to specify array index in the output JSON.
[]'s are only valid on the RHS of the `shift` spec.

Example :
```json
  // input
  {
    "photo-1-id": "327704",
    "photo-1-url": "http://bob.com/0001/327704/photo.jpg"
  }

  // spec
  {
    "photo-1-id": "Photos[1].Id",   // Declare the "Photos" in the output to be an array,
    "photo-1-url": "Photos[1].Url"  // that the 1-th array location should have data

    // same as above but more powerful
    // note `&` logic can be used inside the '[ ]' notation
    "photo-*-url": "Photos[&(0,1)].Url"
  }

  // output
  {
    "Photos": [
      null ,                // note Photos[0] is null, because no data was pushed to it
      {
        "Id":"327704",
        "Url":"http://bob.com/0001/327704/photo.jpg"
      }
    ]
  }
```


3) JSON arrays in the spec file
JSON Arrays in `shift` spec are used to to specify that piece of input data should be copied to two places in the output JSON.
Example :

```json
// input
{ "foo" : 3 }

// spec
{ "foo" : [ "bar", "baz" ] }    // push the 3, to both the of the output paths

// output
{
  "bar" : 3,
  "baz" : 3
}
```


4) Implicit Array creation in the output JSON
If a spec file is configured to output multiple pieces of data to the same output location, the
output location will be turned into a JSON array.
Example :
```json
  // input
  {
      "foo" : "bar",
      "tuna" : "marlin"
  }

  // spec
  {
      "foo"  : "baz",
      "tuna" : "baz"
  }

  // output
  {
      "baz" : [ "bar", "marlin" ]     // Note the order of this Array should not be relied upon
  }
```


Algorithm High Level
 Walk the input data, and `shift` spec simultaneously, and execute the `shift` command/mapping each time
 there is a match.

Algorithm Low Level
- Simultaneously walk of the spec and input JSon, and maintain a walked "input" path data structure.
- Determine a match between input JSON key and LHS spec, by matching LHS spec keys in the following order :
-- Note that `|` keys are are split into their subkeys, eg "literal", `*`, or `&` LHS keys

1) Try to match the input key with "literal" spec key values
2) If no literal match is found, try to match against LHS `&` computed values.
2.1) For deterministic behavior, if there is more than one `&` LHS key, they are applied/matched in alphabetical order,
  after the `&` syntactic sugar is replaced with its canonical form.
3) If no match is found, try to match against LHS keys with `*` wildcard values.
3.1) For deterministic behavior, `*` wildcard keys are sorted and applied/matched in alphabetical order.

Note, processing of the `@` and `$` LHS keys always occur if their parent's match, and do not block any other matching.


Implementation

Instances of this class execute `shift` transformations given a transform spec of Jackson-style maps of maps
and a Jackson-style map-of-maps input.

### The `default` Operation
 `default` is a kind of JOLT transform that applies default values in a non-destructive way.

 For comparison :
 `shift` walks the input data and asks its spec "Where should this go?"
 `default` walks the spec and asks "Does this exist in the data?  If not, add it."

 Example : Given input JSON like
 ```json
 {
   "Rating":3,
   "SecondaryRatings":{
      "quality":{
         "Range":7,
         "Value":3,
         "Id":"quality"
      },
      "sharpness": {
         "Value":4,
         "Id":"sharpness"
      }
   }
 }
 ```
 With the desired output being :
 ```json
 {
   "Rating":3,
   "RatingRange" : 5,
   "SecondaryRatings":{
      "quality":{
         "Range":7,
         "Value":3,
         "Id":"quality",
         "ValueLabel": null,
         "Label": null,
         "MaxLabel": "Great",
         "MinLabel": "Terrible",
         "DisplayType": "NORMAL"
      },
      "sharpness": {
         "Range":5,
         "Value":4,
         "Id":"sharpness",
         "ValueLabel": null,
         "Label": null,
         "MaxLabel": "High",
         "MinLabel": "Low",
         "DisplayType": "NORMAL"
      }
   }
 }
 ```
 This is what the `default` Spec would look like
 ```json
 {
   "RatingRange" : 5,
   "SecondaryRatings": {
     "quality|value" : {
        "ValueLabel": null,
        "Label": null,
        "MaxLabel": "Great",
        "MinLabel": "Terrible",
        "DisplayType": "NORMAL"

     }
     "*": {
        "Range" : 5,
        "ValueLabel": null,
        "Label": null,
        "MaxLabel": "High",
        "MinLabel": "Low",
        "DisplayType": "NORMAL"
     }
   }
 }
 ```

 The Spec file format for `default` are tree Map<String, Object> objects.   `default` handles outputting
  of JSON Arrays via special wildcard in the Spec.

 `default` Spec WildCards and Flag :
 "*" aka STAR : Apply these defaults to all input keys at this level
 "|" aka OR  : Apply these defaults to input keys, if they exist
 "[]" aka : Signal to `default` that the data for this key should be an array.
   This means all `default` keys below this entry have to be "integers".

 Valid Array Specification :
 ```json
 {
   "photos[]" : {
     "2" : {
       "url" : "http://www.bazaarvoice.com",
       "caption" : ""
     }
   }
 }
 ```

 An Invalid Array Specification would be :
 ```json
 {
   "photos[]" : {
     "photo-id-1234" : {
       "url" : "http://www.bazaarvoice.com",
       "caption" : ""
     }
   }
 }
 ```

 Algorithm
 `default` walks its Spec in a depth first way.
 At each level in the Spec tree, `default`, works from most specific to least specific Spec key:
   Literals key values
   "|", sub-sorted by how many or values there, then alphabetically (for deterministic behavior)
   "*"

 At a given level in the `default` Spec tree, only literal keys force `default` to create new entries
  in the input data: either as a single literal value or adding new nested Array or Map objects.
 The wildcard operators, are applied after the literal keys, and will not cause the those keys to be
  added if they are not already present in the input document (either naturally or having been defaulted
  in from literal spec keys).


 Algorithm :
 1) Walk the spec
 2) for each literal key in the spec (specKey)
 2.1) if the the specKey is a map or array, and the input is null, default an empty Map or Array into the output
 2.2.1) re-curse on the literal spec
 2.2) if the the specKey is a map or array, and the input is not null, but of the "wrong" type, skip and do not recurse
 2.2) if the the specKey, is a literal value, default the literal and value into the output and do not recurse
 3) for each wildcard in the spec
 3.1) find all keys from the defaultee that match the wildcard
 3.2) treat each key as a literal speckey

 Corner Cases :

 Due to `default`'s array syntax, we can't actually express that we expect the top level of the input to be an Array.
 The workaround for this is that we check the type of the object that is at the root level of the input.
 If it is a map, no problem.
 If it is an array, we treat the "root" level of the `default` spec, as if it were the child of an Array type `default` entry.
 To force unambiguity, `default` throws an Exception if the input is null.

### The `remove` Operation
 Removr is a kind of JOLT transform that removes content from the input JSON.
 
 For comparison :
 `shift` walks the input data and asks its spec "Where should this go?"
 `default` walks the spec and asks "Does this exist in the data?  If not, add it."

 While, Removr walks the spec and asks "if this exists, remove it."

 Example : Given input JSON like
 ```json
 {
   "~emVersion" : "2",
   "id":"123124",
   "productId":"31231231",
   "submissionId":"34343",
   "this" : "stays",
   "configured" : {
     "a" : "b",
     "c" : "d"
   }
 }
 ```
 With the desired output being :
 ```json
 {
   "id":"123124",
   "this" : "stays",

   "configured" : {
     "a" : "b"
   }
 }
 ```
 This is what the Removr Spec would look like
 ```json
 {
   "~emVersion" : "",
   "productId":"",
   "submissionId":"",

   "configured" : {
     "c" : ""
   }
 }
 ```

  * Removr Wildcards

 `*` Wildcard
   Valid only on the LHS ( input JSON keys ) side of a Removr Spec
   The `*` wildcard can be used by itself or to match part of a key.

   `*` wildcard by itself :
    To remove "all" keys under an input,  use the * by itself on the LHS.
    ```json
    // example input
    {
     "ratings":{
        "Set1":{
           "a":"a",
           "b":"b"
        },
        "Set2":{
            "c":"c",
            "b":"b"
        }
      },
    }
    //desired output
    {
     "ratings":{
        "Set1":{
           "a":"a"
        },
        "Set2":{
            "c":"c"
        }
      },
    }

    //Spec would be
    {
     "ratings":{
        "*":{
          "b":""
        },
      },
    }
    ```
    In this example, "Set1" and "Set2" under rating both have the same structure, and thus we can use the `*`
     to allow use to write more compact rules to remove "b" from all children under ratings. This is especially useful when we don't know
     how many children will  be under ratings, but we would like to nuke certain part of it across.

   `*` wildcard as part of a key :
    This is useful for working with input JSON with keys that are "prefixed".
    Ex : if you had an input document like
    ```json
        {
         "ratings_legacy":{
              "Set1":{
                  "a":"a",
                  "b":"b"
                },
              "Set2":{
                  "a":"a",
                   "b":"b"
               }
           }

         "ratings_new":{
               "Set1":{
                   "a":"a",
                   "b":"b"
               },
               "Set2":{
                   "a":"a",
                   "b":"b"
               }
          }
       }
    ```

    A 'rating_*' would match both keys. As in `shift` wildcard matching, * wildcard is as non greedy as possible, which enable us to give more than one * in key.

    For an ouput that removed Set1 from all ratings_* key, the spec would be,
     ```json
        {
         "ratings_*":{
              "Set1":""
       }
    ```
  * Arrays

 Removr can also handle data in Arrays.

  It can walk thru all the elements of an array with the "*" wildcard.

  Additionally, it can remove individual array indicies.  To do this the LHS key
   must be a number but in String format.

  Example
  ```json
  "spec": {
    "array": {
      "0" : ""
    }
  }
  ```

  In this case, Removr will remove the zero-th item from the input "array", which will cause data at
   index "1" to become the new "0".  Because of this, Remover matches all the literal/explicit
   indices first, sorts them from Biggest to Smallest, then does the removing.

### CARDINALITY

 The CardinalityTransform changes the cardinality of input JSON data elements.
 The impetus for the CardinalityTransform, was to deal with data sources that are inconsistent with
  respect to the cardinality of their returned data.

 For example, say you know that there will be a "photos" element in a document.  If your underlying data
  source is trying to be nice, it may adjust the "type" of the photos element, depending on how many
  photos there actually are.

 Single photo :
 ```json
     "photos" : { "url" : "pants.com/1.jpg" }  // photos element is a "single" map entry
 ```

 Or multiple photos :
 ```json
     "photos" : [
        { "url" : "pants.com/1.jpg" },
        { "url" : "pants.com/2.jpg" }
     ]
 ```

 The `shift` and `default` transforms can't handle that variability, so the CardinalityTransform was
  created to "fix" document, so that the rest of the transforms can _assume_ "photos" will be an Array.


 At a base level, a single Cardinality "command" maps data into a "ONE" or "MANY" state.

 The idea is that you can start with a copy your JSON input and modify it into a Cardinality spec by
  specifying a "cardinality" for each piece of data that you care about changing in the output.
 Input data that are not called out in the spec will remain in the output unchanged.

 For example, given this simple input JSON :
 ```json
 {
   "review" : {
     "rating" : [ 5, 4 ]
   }
 }
 ```
 A simple Cardinality spec could be constructed by specifying that the "rating" should be a single value:
 ```json
 {
   "review" : {
     "rating" : "ONE"
   }
 }
 ```
 would product the following output JSON :
 ```json
 {
   "review" : {
     "rating" : 5
   }
 }
 ```

 In this case, we turn the array "[ 5, 4 ]" into a single value by pulling the first index of the array.
 Hence, the output has "rating : 5".

 Valid Cardinality Values (RHS : right hand side)

 'ONE'
   If the input value is a List, grab the first element in that list, and set it as the data for that element
   For all other input value types, no-op.

 'MANY'
   If the input is not a List, make a list and set the first element to be the input value.
   If the input is "null", make it be an empty list.
   If the input is a list, no-op


 Cardinality Wildcards

 As shown above, Cardinality specs can be entirely made up of literal string values, but wildcards similar
 to some of those used by `shift` can be used.

 `*` Wildcard
   Valid only on the LHS ( input JSON keys ) side of a Cardinality Spec
   Unlike `shift`, the `*` wildcard can only be used by itself. It can be used
   achieve a for/each manner of processing input.

 Let's say we have the following input :
 ```json
 {
   "photosArray" : [
     {
       "url" :  [ "http://pants.com/123-normal.jpg", "http://pants.com/123-thumbnail.jpg" ],
       "caption" : "Nice pants"
     },
     {
       "url" :  [ "http://pants.com/123-thumbnail.jpg", "http://pants.com/123-normal.jpg" ],
       "caption" : "Nice pants"
     }
   ]
 }
 ```
 And we'd like a spec that says "for each item 'url', covert to ONE" :
 ```json
 {
   "photosArray" : {
     "*" : { // for each item in the array
       "url" : "ONE"   // url should be singular
     }
   }
 }
 ```
 Which would yield the following output :
 ```json
 {
   "photosArray" : [
     {
       "url" :  "http://pants.com/123-normal.jpg",
       "caption" : "Nice pants"
     },
     {
       "url" :  "http://pants.com/123-thumbnail.jpg",
       "caption" : "Nice pants"
     }
   ]
 }
 ```

 `@` Wildcard
   Valid only on the LHS of the spec.
   This wildcard should be used when content nested within modified content needs to be modified as well.

 Let's say we have the following input:
 ```json
 {
   "views" : [
     { "count" : 1024 },
     { "count" : 2048 }
   ],
 }
 ```
 The following spec would convert "views" to a ONE and "count" to a MANY :
 ```json
 {
   "views" : {
     "@" : "ONE",
     "count" : "MANY"
   }
 }
 ```
 Yielding the following output:
 ```json
 {
   "views" : {
     "count" : [ 1024 ]
   }
 }
 ```


 Cardinality Logic Table

|INPUT    | CARDINALITY | OUTPUT | NOTE                                                |
| ------- | ----------- | ------ | --------------------------------------------------- |
| String  | ONE         | String |  no-op                                              |
| Number  | ONE         | Number |  no-op                                              |
| Boolean | ONE         | Map    |  no-op                                              |
| Map     | ONE         | Map    |  no-op                                              |
| List    | ONE         | [0]    |  use whatever the first item in the list was        |
| String  | MANY        | List   |  make the input String, be [0] in a new list        |
| Number  | MANY        | List   |  make the input Number, be [0] in a new list        |
| Boolean | MANY        | List   |  make the input Boolean, be [0] in a new list       |
| Map     | MANY        | List   |  make the input Map, be [0] in a new list           |
| List    | MANY        | List   |  no-op                                              |


### SORT

 Recursively sorts all maps within a JSON object into new sorted LinkedHashMaps so that serialized
 representations are deterministic.  Useful for debugging and making test fixtures.

 Note this will make a copy of the input Map and List objects.

 The sort order is standard alphabetical ascending, with a special case for "~" prefixed keys to be bumped to the top.
 
