    /*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

function myGraph() {
        //订阅连线，圆弧连接、不参与force运算，但在tick中更新
        this.slinks =[];
        //订阅发送连线，为了不与订阅连线重合，直线连接，不参与force运算，但在tick中更新
        this.dlinks =[];
        // Add and remove elements on the graph object
        this.addNode = function (idp) {
            if(typeof idp === 'string'){
            	//setBlocker消息可能先到
            	if(idp==this.blocker)
            		nodes.push({"id": idp,color:'lawngreen'});
            	else
            		nodes.push({"id": idp,color:'steelblue'});
                this.addLink(nodes[0].id, idp);
            }
            else{
                nodes.push(idp);
            }
            update();
        };
        //更新node文字描述
        this.setNodeSta = function(id,sta){
            	d3.select("#node_sta_"+id).text(sta);
        }
        
        //更新node文字描述,避免过度频繁刷新
        this.updateNodeSta = function(id,sta){
	        	var tm = new Date().getTime();
	        	//避免过度频繁刷新
	        	if(!tl_map[id] || tm - tl_map[id]>500){
	            	d3.select("#node_sta_"+id).text(sta);
	            	tl_map[id] = tm;
	        	}
        }
       //设置当前出块人
        this.setBlocker = function(id){
        	//setBlocker消息可能先到
        	this.blocker = id;
        	var blocker_last = d3.select(".node_circle_broker");
        	if(!blocker_last.empty()){
            	var sync = blocker_last.attr("sync");
            	if(sync)
            		blocker_last.attr("class","node_circle").attr("fill","seagreen");
            	else
            		blocker_last.attr("class","node_circle").attr("fill","steelblue");
        	}
        	var nd_blocker = d3.select("#node_"+id);
        	if(!nd_blocker.empty()){
        		nd_blocker.attr("class","node_circle_broker").attr("fill","lawngreen");
        	}
        }
        //设置状态-同步完成
        this.setNodeSync = function(id,val){
        	var nd = d3.select("#node_"+id);
        	nd.attr("sync",val);
        	if(this.blocker != id)
        		nd.attr("class","node_circle").attr("fill","seagreen");
        }
        this.removeNode = function (id) {
            var i = 0;
            var slinks = this.slinks;
            var dlinks = this.dlinks;
            var n = findNode(id);
            while (i < links.length) {
                if ((links[i]['source'] == n) || (links[i]['target'] == n)) {
                    links.splice(i, 1);
                }
                else i++;
            }

           i=0;
           var slinks = this.slinks;
           while (i < slinks.length) {
                if ((slinks[i]['source'] == n) || (slinks[i]['target'] == n)) {
                    slinks.splice(i, 1);
                }
                else i++;
            }

           i=0;
           var dlinks = this.dlinks;
           while (i < dlinks.length) {
                if ((dlinks[i]['source'] == n) || (dlinks[i]['target'] == n)) {
                    dlinks.splice(i, 1);
                }
                else i++;
            }

            nodes.splice(findNodeIndex(id), 1);
            update();
        };

        this.removeLink = function (source, target) {
            for (var i = 0; i < links.length; i++) {
                if (links[i].source.id == source && links[i].target.id == target) {
                    links.splice(i, 1);
                    break;
                }
            }
            update();
        };
        this.removeSLink = function (source, target) {
            var slinks = this.slinks;
            for (var i = 0; i < slinks.length; i++) {
                if (slinks[i].source.id == source && slinks[i].target.id == target) {
                    slinks.splice(i, 1);
                    break;
                }
            }
            update();
        };

        this.removeDLink = function (source, target) {
            var dlinks = this.dlinks;
            for (var i = 0; i < dlinks.length; i++) {
                if (dlinks[i].source.id == source && dlinks[i].target.id == target) {
                    dlinks.splice(i, 1);
                    break;
                }
            }
            update();
        };

        this.removeallLinks = function () {
            links.splice(0, links.length);
            update();
        };

        this.removeAllNodes = function () {
            nodes.splice(0, links.length);
            update();
        };

        this.addLink = function (source, target, value) {
            links.push({"source": findNode(source), "target": findNode(target), "value": value||20});
            update();
        };
        this.addSLink=function(source, target, value){
            this.slinks.push({"source": findNode(source), "target": findNode(target), "value": value||20});
            update();
        }
        this.addDLink=function(source, target, value){
            this.dlinks.push({"source": findNode(source), "target": findNode(target), "value": value||20});
            update();
        }

        var findNode = function (id) {
            for (var i in nodes) {
                if (nodes[i]["id"] === id) return nodes[i];
            }
            ;
        };

        var findNodeIndex = function (id) {
            for (var i = 0; i < nodes.length; i++) {
                if (nodes[i].id == id) {
                    return i;
                }
            }
            ;
        };

        // set up the D3 visualisation in the specified element
        var wh = Math.min($( document ).width(),$( document ).height());
        var w = wh,h = wh;
        this.w = w; this.h = h;

        var color = d3.scale.category10();
        var svg = d3.select("body").append("svg")
            .attr("width", w)
            .attr("height", h);      
        this.svg = svg;  
//draw circle
/*        var circle = svg.append("circle") 
        .attr("cx", w/2)
        .attr("cy", h/2)
        .attr("r", w/2.4).attr("id","circle_cluster");*/
// build the arrow.
svg.append("svg:defs").selectAll("marker")
    .data(["end"])      // Different link/path types can be defined here
  .enter().append("svg:marker")    // This section adds in the arrows
    .attr("id", String)
    .attr("viewBox", "0 -5 10 10")
    .attr("refX", 15)
    .attr("refY", -1.5)
    .attr("markerWidth", 6)
    .attr("markerHeight", 6)
    .attr("orient", "auto")
    .attr("opacity",0.1)
  .append("svg:path")
    .attr("d", "M0,-5L10,0L0,5");


        var vis = svg
                .attr("id", "svg")
                .attr("pointer-events", "all")
                .attr("viewBox", "0 0 " + w + " " + h)
                .attr("perserveAspectRatio", "xMinYMid")
                .append('svg:g');

        var force = d3.layout.force();


        var nodes = force.nodes(),
                links = force.links();
        //force自己的缓存自己初始化会重置，slinks是非force的缓存，需要自己重置
        this.slinks = [];
        this.dlinks = [];
        var dlinks = this.dlinks;
        var slinks = this.slinks;
        var update = function () {
        	
      //console.error(slinks.length+':'+vis.selectAll(".slink")[0].length)
	  var slink = vis.selectAll(".slink")
	    .data(slinks,function (d) {
            return 'p_'+d.source.id + "-" + d.target.id;
        });
	  slink.enter().append("svg:path")
          .attr("id", function (d) {
            return 'p_'+d.source.id + "-" + d.target.id;
        }).attr("class","slink")
	    .attr("marker-end", "url(#end)");
        slink.exit().remove();
	  
      var dlink = vis.selectAll(".dlink")
        .data(dlinks, function (d) {
            return 'd_'+d.source.id + "-" + d.target.id;
        });

        dlink.enter().append("line")
         //.attr("marker-end", "url(#end)")
            .attr("id", function (d) {
                return 'd_'+d.source.id + "-" + d.target.id;
            })
            .attr("stroke-width", function (d) {
                return d.value / 10;
            })
            .attr("class", "dlink");
        dlink.exit().remove();
        //不显示node之间的直线连接
      //只取第一个画圆用
        var links0 = links.length==0?[]:[links[0]];
        var bc = vis.selectAll(".back_circle")
   		.data(links0, function (d) {
                return "bc_"+d.source.id + "-" + d.target.id;
            });

	    bc.enter().append("svg:circle")
	        .attr("id", function (d) {
	            return "l_"+d.source.id + "-" + d.target.id;
	        }).attr("r", function(d) { return 100 })
	        .attr("cx", function (d) {
                    return d.source.x;
                }) .attr("cy", function (d) {
                    return d.source.y;
                })
	        .attr("stroke-width", function (d) {
	            return d.value / 10;
	        }) .attr("class", "back_circle");
	    bc.exit().remove();
       
          /* var link = vis.selectAll(".link")
                 .data(links, function (d) {
                        return "l_"+d.source.id + "-" + d.target.id;
                    });

            link.enter().append("line")
             //.attr("marker-end", "url(#end)")
                    .attr("id", function (d) {
                        return "l_"+d.source.id + "-" + d.target.id;
                    })
                    .attr("stroke-width", function (d) {
                        return d.value / 10;
                    })
                    .attr("class", "link");
            link.append("title")
                    .text(function (d) {
                        return d.value;
                    });
            link.exit().remove();*/

            var node = vis.selectAll("g.node")
                    .data(nodes, function (d) {
                        return d.id;
                    });


            var nodeEnter = node.enter().append("g")
                    .attr("class", "node")
                    .call(force.drag);

            nodeEnter.append("svg:circle")
                    .attr("r", function(d) { return d.r|| 12 })
                    .attr("id", function (d) {
                        return "node_" + d.id;
                    })
                    .attr("class", "node_circle")
                    .attr("fill", function(d) { return d.color|| color(d.id); });

            nodeEnter.append("svg:text")
                    .attr("class", "textClass")
                    .attr("x", 14)
                    .attr("y", ".31em")
                    .text(function (d) {
                        return d.id;
                    });

            nodeEnter.append("svg:text")
            .attr("class", "textClass")
            .attr("x", 14)
            .attr("y", "20")
            .attr("id", function (d) {
                return "node_sta_" + d.id;
            })
            .text(function (d) {
                return d.sta||'';
            });

            node.exit().remove();

            

            force.on("tick", function () {

                node.attr("transform", function (d) {
                    return "translate(" + d.x + "," + d.y + ")";
                });

                slink.attr("d", function(d) {
                var dx = d.target.x - d.source.x,
                    dy = d.target.y - d.source.y,
                    dr = Math.sqrt(dx * dx + dy * dy);
                return "M" + 
                    d.source.x + "," + 
                    d.source.y + "A" + 
                    dr + "," + dr + " 0 0,1 " + 
                    d.target.x + "," + 
                    d.target.y;
                });
                
                dlink.attr("x1", function (d) {
                    return d.source.x;
                }) .attr("y1", function (d) {
                    return d.source.y;
                })
                .attr("x2", function (d) {
                    return d.target.x;
                })
                .attr("y2", function (d) {
                    return d.target.y;
                });
               
                //动态调整背景圆
                bc.attr("r", function (d) {
	                return Math.sqrt((d.source.x-d.target.x)*(d.source.x-d.target.x) 
	                	+(d.source.y-d.target.y)*(d.source.y-d.target.y));
	            });
              
               /*link.attr("x1", function (d) {
                    return d.source.x;
                }) .attr("y1", function (d) {
                    return d.source.y;
                })
                .attr("x2", function (d) {
                    return d.target.x;
                })
                .attr("y2", function (d) {
                    return d.target.y;
                });*/
            });

            // Restart the force layout.
            force.gravity(.1)
            //.charge(-8000000)
            .charge(function(d){
                var charge = -2000;
                if (d.index === 0) {
                    charge = 10 * charge;
                    //d.fixed = true;
                }
                return charge;
            })
            //.friction(0)
            .linkDistance( function(d) { return d.value * 8 } )
            .size([w, h])
            .start();
        };


        // Make it all go
        update();
    }
