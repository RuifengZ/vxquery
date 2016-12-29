(: Licensed to the Apache Software Foundation (ASF) under one
   or more contributor license agreements.  See the NOTICE file
   distributed with this work for additional information
   regarding copyright ownership.  The ASF licenses this file
   to you under the Apache License, Version 2.0 (the
   "License"); you may not use this file except in compliance
   with the License.  You may obtain a copy of the License at
   
     http://www.apache.org/licenses/LICENSE-2.0
   
   Unless required by applicable law or agreed to in writing,
   software distributed under the License is distributed on an
   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
   KIND, either express or implied.  See the License for the
   specific language governing permissions and limitations
   under the License. :)

(: 
XQuery Join Aggregate Query
-------------------
Count all sensor readings for TMIN in 2001.
:)
count(
    let $sensor_collection := "/tmp/1.0_partition_ghcnd_all_xml/sensors"
    for $r in collection($sensor_collection)
    for $data in $r("dataCollection")("data") 
    let $date := xs:date(fn:substring(xs:string(fn:data($data("date"))), 0, 11))
    where $data("dataType") eq "TMIN" 
        and fn:year-from-date($date) eq 2001
    return $data("value")
)