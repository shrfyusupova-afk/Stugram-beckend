import 'package:flutter/material.dart';

class CommentSection extends StatefulWidget {
  @override
  _CommentSectionState createState() => _CommentSectionState();
}

class _CommentSectionState extends State<CommentSection> {
  bool isExpanded = false;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        GestureDetector(
          onTap: () {
            setState(() {
              isExpanded = !isExpanded;
            });
          },
          child: Container(
            color: Colors.grey[300], // Liquid glass style
            child: Text('Comments', style: TextStyle(fontSize: 16)),
          ),
        ),
        if (isExpanded)
          Column(
            children: [
              TextField(
                decoration: InputDecoration(hintText: 'Write a comment...'),
              ),
              Container(
                height: 200,
                color: Colors.grey[200],
                child: ListView(
                  children: [Text('Comment 1'), Text('Comment 2')],
                ),
              ),
            ],
          ),
      ],
    );
  }
}
